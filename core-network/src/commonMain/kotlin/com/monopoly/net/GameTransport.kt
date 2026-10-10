package com.monopoly.net

import kotlinx.coroutines.flow.Flow

/**
 * Antarmuka transport sesi (cetak biru dokumen 12). Implementasi:
 * WifiTransport (TCP + UDP discovery) dan BluetoothTransport (Kable).
 * Semua aman dipanggil dari coroutine; [incoming] mengalir pesan yang
 * sudah terframing & terparse.
 */
interface GameTransport {
    /** Nama transport untuk UI ("WiFi", "Bluetooth"). */
    val kind: String

    /** true bila koneksi aktif. */
    val isConnected: Boolean

    /** Aliran pesan masuk (sudah terparse dari WireProtocol). */
    val incoming: Flow<NetMessage>

    /** Kirim pesan (framing otomatis). */
    suspend fun send(msg: NetMessage)

    /** Tutup koneksi dengan bersih. */
    suspend fun close()

    companion object {
        /** Port TCP sesi (WiFi). */
        const val TCP_PORT = 47777

        /** Port UDP discovery (WiFi). */
        const val DISCOVERY_PORT = 47778

        /** UUID layanan BLE (host advertise, guest scan). */
        const val BLE_SERVICE_UUID = "8f2e1c4a-9d3b-4e5f-a1c2-000000000001"

        /** Characteristic guest → host (write). */
        const val BLE_CMD_CHAR_UUID = "8f2e1c4a-9d3b-4e5f-a1c2-000000000002"

        /** Characteristic host → guest (notify). */
        const val BLE_STATE_CHAR_UUID = "8f2e1c4a-9d3b-4e5f-a1c2-000000000003"

        /** Prefix nama advertise/discovery. */
        const val DEVICE_PREFIX = "MONOPOLI"
    }
}

/** Status koneksi untuk UI. */
enum class ConnState { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, FAILED }

/** Perangkat yang terlihat saat scan (untuk UI Gabung). */
data class DiscoveredDevice(
    val id: String,
    val name: String,
    val kind: String,          // "WiFi" | "Bluetooth"
    val address: String        // ip:port (WiFi) atau identifier BLE
)
