package com.monopoly.net

import kotlinx.coroutines.CoroutineScope

/**
 * Web: multiplayer belum didukung (menyusul lewat WebSocket, cetak biru
 * dokumen 12 fase uji). Fungsi gagal cepat agar UI bisa tampilkan pesan.
 */
actual fun scanHosts(scope: CoroutineScope, onFound: (DiscoveredDevice) -> Unit): () -> Unit = { }

actual suspend fun joinHost(device: DiscoveredDevice, scope: CoroutineScope): GameTransport? = null

actual suspend fun openHostServer(
    hostName: String,
    scope: CoroutineScope,
    onGuest: suspend (GameTransport) -> Unit
): Int = 0
