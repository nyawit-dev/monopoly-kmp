package com.monopoly.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Manifest mod (cetak biru dokumen 03). Field wajib tanpa default agar
 * parse gagal dengan pesan jelas bila hilang.
 */
@Serializable
data class ModManifest(
    val id: String,
    val name: String,
    val version: String,
    val api_version: Int,
    val author: String = "anonim",
    val capabilities: List<String> = emptyList(),
    val depends: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val priority: Int = 100,
    val enabled: Boolean = true,
    val roles: List<String> = emptyList(),
    val description: String = ""
)

/** Capability yang dikenal (cetak biru dokumen 07). */
val KNOWN_CAPABILITIES: Set<String> = setOf(
    "read_state", "modify_money", "move_players", "jail_control",
    "draw_cards", "ui_message", "ui_overlay", "asset_override"
)

/** Peran yang dikenal (cetak biru dokumen 05/06). */
val KNOWN_ROLES: Set<String> = setOf("rules", "board", "cards", "texts", "theme", "assets")

/** Semver sederhana (MAJOR.MINOR.PATCH; MINOR/PATCH boleh kosong). */
data class SemVer(val major: Int, val minor: Int, val patch: Int) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int = when {
        major != other.major -> major - other.major
        minor != other.minor -> minor - other.minor
        else -> patch - other.patch
    }

    companion object {
        private val RE = Regex("""^(\d+)\.(\d+)(?:\.(\d+))?$""")
        fun parse(s: String): SemVer? {
            val m = RE.find(s.trim()) ?: return null
            return SemVer(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues.getOrNull(3)?.toIntOrNull() ?: 0)
        }
    }
}

/**
 * Cek apakah [version] memenuhi [range] semver. Bentuk yang didukung:
 * `^1.0` (>=1.0.0 <2.0.0), `>=1.2`, `<=1.2`, `<2`, `1.2.3` (persis), dan
 * konjungsi dipisah spasi (`>=1.2 <2`). Range kosong → true.
 */
fun satisfies(version: String, range: String): Boolean {
    val v = SemVer.parse(version) ?: return false
    val parts = range.trim().split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return true
    return parts.all { part ->
        when {
            part.startsWith("^") -> SemVer.parse(part.drop(1))
                ?.let { v >= it && v.major == it.major } ?: false
            part.startsWith(">=") -> SemVer.parse(part.drop(2))?.let { v >= it } ?: false
            part.startsWith("<=") -> SemVer.parse(part.drop(2))?.let { v <= it } ?: false
            part.startsWith("<") -> SemVer.parse(part.drop(1))?.let { v < it } ?: false
            else -> SemVer.parse(part)?.let { it == v } ?: false
        }
    }
}

private val manifestJson = Json { ignoreUnknownKeys = true }

/** Parse teks mod.json → manifest atau pesan galat actionable. */
fun parseManifest(text: String): Result<ModManifest> = runCatching {
    manifestJson.decodeFromString<ModManifest>(text)
}.recoverCatching { e ->
    throw IllegalArgumentException("mod.json tidak valid: ${e.message?.lineSequence()?.first() ?: e}")
}

/** Kunci tak dikenal di mod.json (untuk warning Log Mod). */
fun unknownManifestKeys(text: String): List<String> {
    val obj = runCatching { manifestJson.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
    val known = setOf(
        "id", "name", "version", "author", "api_version", "capabilities",
        "depends", "conflicts", "priority", "enabled", "roles", "description"
    )
    return obj.keys.filterNot { it in known }
}
