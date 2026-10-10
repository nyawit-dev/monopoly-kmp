package com.monopoly.net

import kotlinx.serialization.json.Json

/**
 * Framing pesan (cetak biru dokumen 12): **4 byte big-endian panjang**
 * + payload UTF-8 JSON. Aman untuk TCP maupun BLE (MTU kecil →
 * penerima menyusun potongan sampai panjang terpenuhi).
 */
object WireProtocol {

    const val HEADER_SIZE = 4
    const val MAX_PAYLOAD = 256 * 1024 // batas wajar; pesan > ini = bug

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /** Bungkus [msg] jadi bingkai siap kirim. */
    fun encode(msg: NetMessage): ByteArray {
        val payload = json.encodeToString(NetMessage.serializer(), msg).encodeToByteArray()
        require(payload.size <= MAX_PAYLOAD) { "pesan terlalu besar: ${payload.size}" }
        val frame = ByteArray(HEADER_SIZE + payload.size)
        frame[0] = ((payload.size ushr 24) and 0xFF).toByte()
        frame[1] = ((payload.size ushr 16) and 0xFF).toByte()
        frame[2] = ((payload.size ushr 8) and 0xFF).toByte()
        frame[3] = (payload.size and 0xFF).toByte()
        payload.copyInto(frame, HEADER_SIZE)
        return frame
    }

    /** Bongkar bingkai → pesan. */
    fun decode(frame: ByteArray): NetMessage =
        json.decodeFromString(NetMessage.serializer(), frame.decodeToString())

    /**
     * Penerima bingkai: beri potongan data lewat [feed]; setiap kali satu
     * pesan utuh terbentuk, [onMessage] dipanggil. Menyimpan sisa di
     * buffer internal. Dirancang untuk chunk BLE maupun aliran TCP.
     */
    class FrameReceiver(private val onMessage: (NetMessage) -> Unit) {
        private val buffer = ArrayList<Byte>()
        private var expected = -1

        fun feed(data: ByteArray) {
            buffer.addAll(data.toList())
            drain()
        }

        fun feed(text: String) = feed(text.encodeToByteArray())

        private fun drain() {
            while (true) {
                if (expected < 0) {
                    if (buffer.size < HEADER_SIZE) return
                    expected = ((buffer[0].toInt() and 0xFF) shl 24) or
                        ((buffer[1].toInt() and 0xFF) shl 16) or
                        ((buffer[2].toInt() and 0xFF) shl 8) or
                        (buffer[3].toInt() and 0xFF)
                    if (expected > MAX_PAYLOAD) {
                        // Korupsi: buang buffer, mulai ulang.
                        buffer.clear()
                        expected = -1
                        return
                    }
                    repeat(HEADER_SIZE) { buffer.removeAt(0) }
                }
                if (buffer.size < expected) return
                val payload = ByteArray(expected)
                for (i in 0 until expected) payload[i] = buffer[i]
                repeat(expected) { buffer.removeAt(0) }
                expected = -1
                onMessage(decode(payload))
            }
        }

        fun reset() {
            buffer.clear()
            expected = -1
        }
    }
}
