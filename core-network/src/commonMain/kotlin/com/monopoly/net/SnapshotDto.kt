package com.monopoly.net

import kotlinx.serialization.Serializable

/**
 * State game versi kabel (cetak biru dokumen 12 §SnapshotDto). Satu tipe
 * ini dipakai UI lokal maupun jaringan: engine mengisi lewat [toSnapshotDto],
 * guest merender dari sini — tak ada simulasi lokal di guest.
 */
@Serializable
data class SnapshotDto(
    val players: List<PlayerDto>,
    val tiles: List<TileDto>,
    val currentPlayerIndex: Int,
    val phase: String,
    val message: String? = null,
    val lastRoll: DiceDto? = null,
    val eventCard: String? = null,
    val consecutiveDoubles: Int = 0,
    val turnNumber: Int = 0
)

@Serializable
data class PlayerDto(
    val name: String,
    val money: Int,
    val position: Int,
    val inJail: Boolean,
    val jailTurns: Int,
    val jailCards: Int,
    val properties: List<Int>,
    val isBankrupt: Boolean
)

@Serializable
data class TileDto(
    val index: Int,
    val ownerName: String? = null,
    val houses: Int = 0,
    val mortgaged: Boolean = false
)

@Serializable
data class DiceDto(val d1: Int, val d2: Int, val total: Int, val isDouble: Boolean)

/**
 * Tambalan state (cetak biru dokumen 12 §Anggaran bandwidth): hanya
 * yang berubah. Pemain dipetakan ke indeks (hemat: nama tak diulang);
 * nilai null = pemain keluar (bangkrut). Petak hanya yang berubah.
 */
@Serializable
data class SnapshotDelta(
    val currentPlayerIndex: Int? = null,
    val phase: String? = null,
    val message: String? = null,
    val lastRoll: DiceDto? = null,
    val eventCard: String? = null,
    val consecutiveDoubles: Int? = null,
    val turnNumber: Int? = null,
    val players: Map<Int, PlayerDto?> = emptyMap(),
    val tiles: Map<Int, TileDto> = emptyMap()
)

/** Fase game sebagai string (sama dengan GamePhase; aman untuk JSON). */
object Phases {
    const val WAITING_FOR_ROLL = "WAITING_FOR_ROLL"
    const val ROLLING = "ROLLING"
    const val MOVING = "MOVING"
    const val RESOLVING_ACTION = "RESOLVING_ACTION"
    const val GAME_OVER = "GAME_OVER"
}

/** Aksi yang boleh dikirim guest lewat [NetMessage.Command]. */
object Actions {
    const val ROLL = "roll"
    const val BUY = "buy"
    const val SKIP = "skip"
    const val PAY_FINE = "pay_fine"
    const val BUILD_HOUSE = "build_house"
    const val SELL_HOUSE = "sell_house"
    const val MORTGAGE = "mortgage"
    const val UNMORTGAGE = "unmortgage"
}
