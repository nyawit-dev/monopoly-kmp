package com.monopoly.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Implementasi WiFi (TCP KorIO + UDP broadcast) — desktop/Android JVM. */
actual fun scanHosts(scope: CoroutineScope, onFound: (DiscoveredDevice) -> Unit): () -> Unit =
    WifiDiscovery.scan(scope, onFound)

actual suspend fun joinHost(device: DiscoveredDevice, scope: CoroutineScope): GameTransport? {
    // address = "ip:port" (lihat WifiDiscovery.scan).
    val parts = device.address.split(":")
    val host = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val port = parts.getOrNull(1)?.toIntOrNull() ?: GameTransport.TCP_PORT
    return WifiTransport.connect(host, port, scope)
}

actual suspend fun openHostServer(
    hostName: String,
    scope: CoroutineScope,
    onGuest: suspend (GameTransport) -> Unit
): Int {
    val server = WifiHostServer(scope)
    val port = server.start(0)
    if (port <= 0) return 0
    WifiDiscovery.advertise(hostName, port, scope)
    // Terima guest berulang selama server hidup (umur aplikasi).
    scope.launch {
        try {
            while (true) server.acceptOne(onGuest)
        } catch (_: Exception) {
            // server ditutup: berhenti
        }
    }
    return port
}
