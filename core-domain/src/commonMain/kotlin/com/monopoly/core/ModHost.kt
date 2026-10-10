package com.monopoly.core

import kotlinx.coroutines.flow.StateFlow

/**
 * Status sebuah mod (cetak biru dokumen 02).
 */
enum class ModStatus {
    /** Semua peran berjalan. */
    ACTIVE,
    /** Berjalan, tapi sebagian peran mati (lihat [ModInfo.error]). */
    ACTIVE_DEGRADED,
    /** Dimatikan pengguna lewat Mod Manager. */
    DISABLED,
    /** Gagal validate/resolve — dinonaktifkan otomatis. */
    ERROR
}

/**
 * Info satu mod untuk Mod Manager UI (read-only snapshot).
 */
data class ModInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val status: ModStatus,
    val priority: Int,
    val enabled: Boolean,
    val roles: Set<String>,
    val capabilities: Set<String>,
    val depends: List<String>,
    val conflicts: List<String>,
    val error: String? = null
)

/**
 * Satu baris Log Mod.
 */
data class ModLogEntry(
    val modId: String,
    val level: String, // "INFO" | "WARN" | "ERROR"
    val message: String
)

/**
 * Port host mod. Implementasi JVM memakai Lua (mods/ folder); platform lain
 * memakai daftar kosong (tanpa mod). Spesifikasi: docs/cetak-biru/02.
 */
interface ModHostPort {
    /** Daftar mod (termasuk basis "inti"), terurut prioritas desc. */
    val mods: StateFlow<List<ModInfo>>

    /** Log terbaru di akhir daftar (mis. maks 100 baris). */
    val log: StateFlow<List<ModLogEntry>>

    /** Rescan + muat ulang semua mod; true bila proses selesai (status per mod lihat [mods]). */
    fun reload(): Boolean

    /** Aktif/nonaktifkan mod (ditulis ke mods/state.json, lalu reload). */
    fun setEnabled(id: String, enabled: Boolean)

    /** Ubah prioritas mod (ditulis ke mods/state.json, lalu reload). */
    fun setPriority(id: String, priority: Int)
}
