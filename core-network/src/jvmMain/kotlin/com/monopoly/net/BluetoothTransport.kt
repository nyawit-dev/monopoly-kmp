package com.monopoly.net

import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Transport Bluetooth LE (cetak biru dokumen 12) — sisi GUEST (central):
 * scan perangkat host, hubungkan, kirim lewat characteristic `cmd`,
 * terima lewat `state` (notify). Framing WireProtocol menangani MTU
 * kecil BLE (potongan disusun ulang penerima).
 *
 * CATATAN: sisi HOST (peripheral/advertise) tidak tersedia di pustaka
 * Kable untuk desktop; host Bluetooth memakai BLE advertise bawaan
 * platform (Android) pada fase uji perangkat. Lihat cetak biru 12.
 */
class BluetoothTransport private constructor(
    private val peripheral: Peripheral,
    private val scope: CoroutineScope
) : GameTransport {

    override val kind: String = "Bluetooth"

    @Volatile
    private var alive = true
    override val isConnected: Boolean get() = alive

    // Channel UNLIMITED: pesan tak hilang walau kolektor belum pasang.
    private val queue = Channel<NetMessage>(Channel.UNLIMITED)
    override val incoming: Flow<NetMessage> = queue.receiveAsFlow()

    private val receiver = WireProtocol.FrameReceiver { msg -> queue.trySend(msg) }

    /** Characteristic host→guest (notify); null sampai layanan ditemukan. */
    private fun stateCharacter() =
        peripheral.services.value?.firstOrNull()
            ?.characteristics?.firstOrNull {
                it.characteristicUuid == UUID.fromString(GameTransport.BLE_STATE_CHAR_UUID)
            }

    /** Characteristic guest→host (write). */
    private fun cmdCharacter() =
        peripheral.services.value?.firstOrNull()
            ?.characteristics?.firstOrNull {
                it.characteristicUuid == UUID.fromString(GameTransport.BLE_CMD_CHAR_UUID)
            }

    init {
        scope.launch {
            try {
                // Tunggu layanan ditemukan setelah connect.
                val services = peripheral.services.first { !it.isNullOrEmpty() }!!
                val stateChar = services.first().characteristics.first {
                    it.characteristicUuid == UUID.fromString(GameTransport.BLE_STATE_CHAR_UUID)
                }
                peripheral.observe(stateChar).collect { receiver.feed(it) }
            } catch (_: Exception) {
                // putus/gagal subscribe: matikan transport
            }
            alive = false
        }
    }

    override suspend fun send(msg: NetMessage) {
        val frame = WireProtocol.encode(msg)
        val cmdChar = cmdCharacter() ?: return
        peripheral.write(cmdChar, frame, WriteType.WithResponse)
    }

    override suspend fun close() {
        alive = false
        runCatching { peripheral.disconnect() }
    }

    companion object {
        private val seen = ConcurrentHashMap<String, Advertisement>()

        /** Guest: pindai host; [onFound] per perangkat. */
        fun scan(scope: CoroutineScope, onFound: (DiscoveredDevice) -> Unit): () -> Unit {
            val job = scope.launch {
                val scanner = Scanner {
                    filters {
                        match {
                            services = listOf(UUID.fromString(GameTransport.BLE_SERVICE_UUID))
                        }
                    }
                }
                scanner.advertisements.collect { ad ->
                    seen[ad.identifier] = ad
                    val name = ad.name ?: ad.peripheralName ?: ad.identifier
                    onFound(DiscoveredDevice(ad.identifier, name, "Bluetooth", ad.identifier))
                }
            }
            return { job.cancel() }
        }

        /** Guest: hubungkan ke host dari hasil [scan]. */
        suspend fun connect(identifier: String, scope: CoroutineScope): BluetoothTransport? {
            val ad = seen[identifier] ?: return null
            val peripheral = Peripheral(ad)
            peripheral.connect()
            return BluetoothTransport(peripheral, scope)
        }
    }
}
