package com.monopoly.net

import kotlinx.coroutines.CoroutineScope

/**
 * Titik masuk jaringan per platform (cetak biru dokumen 12). UI di
 * commonMain hanya memakai tiga fungsi ini; implementasi WiFi ada di
 * jvmMain (web menyusul lewat WebSocket).
 */
expect fun scanHosts(scope: CoroutineScope, onFound: (DiscoveredDevice) -> Unit): () -> Unit

/** Hubungkan ke host dari hasil [scanHosts]; null bila gagal. */
expect suspend fun joinHost(device: DiscoveredDevice, scope: CoroutineScope): GameTransport?

/**
 * Buka server host + mulai advertise. Kembalikan port TCP (0 = gagal).
 * [onGuest] dipanggil sekali per koneksi guest baru (transport siap pakai).
 */
expect suspend fun openHostServer(
    hostName: String,
    scope: CoroutineScope,
    onGuest: suspend (GameTransport) -> Unit
): Int
