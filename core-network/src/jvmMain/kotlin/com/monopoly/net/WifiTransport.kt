package com.monopoly.net

import korlibs.io.socket.AsyncSocket
import korlibs.io.socket.JvmNioAsyncClient
import korlibs.io.socket.JvmNioAsyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.MulticastSocket
import java.nio.channels.AsynchronousSocketChannel

/**
 * Transport WiFi/LAN (cetak biru dokumen 12): TCP KorIO untuk data,
 * UDP broadcast untuk penemuan host. JVM-only (Android pakai kelas ini
 * juga; iOS memakai Bluetooth atau WebSocket).
 */
class WifiTransport private constructor(
    private val socket: AsyncSocket,
    private val scope: CoroutineScope
) : GameTransport {

    override val kind: String = "WiFi"
    override val isConnected: Boolean get() = socket.connected

    // Channel UNLIMITED: pesan tak hilang walau kolektor belum pasang
    // (balapan antara data masuk & subscribe di UI).
    private val queue = Channel<NetMessage>(Channel.UNLIMITED)
    override val incoming: Flow<NetMessage> = queue.receiveAsFlow()

    private val receiver = WireProtocol.FrameReceiver { msg -> queue.trySend(msg) }

    init {
        scope.launch { readLoop() }
    }

    private suspend fun readLoop() {
        val buf = ByteArray(4096)
        try {
            while (socket.connected) {
                val n = socket.read(buf, 0, buf.size)
                if (n <= 0) break
                receiver.feed(buf.copyOf(n))
            }
        } catch (_: Exception) {
            // koneksi putus: loop berhenti, UI melihat isConnected = false
        }
    }

    override suspend fun send(msg: NetMessage) {
        val frame = WireProtocol.encode(msg)
        socket.write(frame, 0, frame.size)
    }

    override suspend fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /** Guest: hubungkan ke host. */
        suspend fun connect(host: String, port: Int, scope: CoroutineScope): WifiTransport {
            val channel = AsynchronousSocketChannel.open()
            val sock = JvmNioAsyncClient(channel)
            sock.connect(host, port)
            return WifiTransport(sock, scope)
        }

        /** Host: bungkus soket hasil accept() jadi transport guest. */
        fun fromSocket(socket: AsyncSocket, scope: CoroutineScope): WifiTransport =
            WifiTransport(socket, scope)
    }
}

/**
 * Penemuan host lewat UDP broadcast (cetak biru dokumen 12 §Penemuan).
 * Host memancarkan `MONOPOLI|<nama>|<port>` tiap 2 detik; guest
 * mendengarkan dan melaporkan perangkat yang terlihat.
 */
object WifiDiscovery {

    private const val BEACON_INTERVAL_MS = 2000L

    /** Host: mulai pancarkan beacon; kembalikan fungsi stop. */
    fun advertise(name: String, port: Int, scope: CoroutineScope): () -> Unit {
        var running = true
        scope.launch {
            val payload = "${GameTransport.DEVICE_PREFIX}|$name|$port".toByteArray()
            DatagramSocket().use { ds ->
                ds.broadcast = true
                while (running) {
                    runCatching {
                        ds.send(
                            DatagramPacket(
                                payload, payload.size,
                                InetAddress.getByName("255.255.255.255"),
                                GameTransport.DISCOVERY_PORT
                            )
                        )
                    }
                    kotlinx.coroutines.delay(BEACON_INTERVAL_MS)
                }
            }
        }
        return { running = false }
    }

    /** Guest: dengarkan beacon; [onFound] dipanggil per perangkat. */
    fun scan(scope: CoroutineScope, onFound: (DiscoveredDevice) -> Unit): () -> Unit {
        var running = true
        scope.launch {
            val socket = DatagramSocket(GameTransport.DISCOVERY_PORT, InetAddress.getByName("0.0.0.0"))
            val buf = ByteArray(256)
            while (running) {
                runCatching {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length)
                    val parts = text.split("|")
                    if (parts.size == 3 && parts[0] == GameTransport.DEVICE_PREFIX) {
                        onFound(
                            DiscoveredDevice(
                                id = "${packet.address.hostAddress}:${parts[2]}",
                                name = parts[1],
                                kind = "WiFi",
                                address = "${packet.address.hostAddress}:${parts[2]}"
                            )
                        )
                    }
                }
            }
            socket.close()
        }
        return { running = false }
    }
}

/**
 * Server sesi WiFi: menerima koneksi guest. Host memakai
 * [startHosting] lalu [acceptLoop] untuk membungkus tiap koneksi.
 */
class WifiHostServer(private val scope: CoroutineScope) {
    private var server: JvmNioAsyncServer? = null

    /** Buka server di [port]; port 0 = pilih otomatis. */
    suspend fun start(port: Int = GameTransport.TCP_PORT): Int {
        val s = JvmNioAsyncServer(port, "0.0.0.0")
        runCatching { s.init() }
        server = s
        return s.port
    }

    /** Terima koneksi berurutan; setiap guest dibungkus WifiTransport. */
    suspend fun acceptOne(onGuest: suspend (WifiTransport) -> Unit) {
        val s = server ?: return
        val sock = s.accept()
        onGuest(WifiTransport.fromSocket(sock, scope))
    }

    suspend fun close() {
        runCatching { server?.close() }
        server = null
    }
}
