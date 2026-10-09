package com.monopoly.core

import kotlinx.serialization.Serializable

/**
 * Tipe petak papan. Menentukan aturan saat pemain mendarat.
 * Dipisah sebagai enum agar core-domain tidak bergantung pada parser JSON.
 */
enum class TileType {
    CORNER,      // START, JAIL, FREE_PARKING, GO_TO_JAIL
    PROPERTY,    // Properti yang bisa dibeli & disewakan
    RAILROAD,    // Stasiun (sewa naik per jumlah stasiun dimiliki)
    UTILITY,     // Listrik/Air (sewa dikali angka dadu)
    EVENT        // Chance / Community Chest
}

/**
 * Petak papan. [colorGroup] dan [housePrice]/[rentSchedule] hanya relevan
 * untuk PROPERTY; nullable agar CORNER/EVENT tetap ringan.
 */
@Serializable
data class Tile(
    val index: Int,
    val name: String,
    val type: TileType,
    val price: Int = 0,
    val colorGroup: String? = null,
    val baseRent: Int = 0,
    val housePrice: Int = 0,
    val mortgageValue: Int = 0,
    val isCorner: Boolean = type == TileType.CORNER,
    val isStart: Boolean = false
)

/**
 * Pemain. [money] dan [position] mutable agar state game mudah di-track,
 * tapi GameState di atasnya dianggap immutable snapshot untuk UI.
 */
data class Player(
    val id: Int,
    val name: String,
    val colorHex: String,
    var money: Int = STARTING_CASH,
    var position: Int = 0,
    var inJail: Boolean = false,
    var jailTurns: Int = 0,
    var isBankrupt: Boolean = false,
    val ownedProperties: MutableSet<Int> = mutableSetOf()
) {
    companion object {
        const val STARTING_CASH = 1500
        const val GO_SALARY = 200
    }
}

/** Fase giliran. Mengunci input UI agar tak ada aksi ganda (anti race). */
enum class GamePhase {
    WAITING_FOR_ROLL,
    ROLLING,
    MOVING,
    RESOLVING_ACTION,
    TURN_END,
    GAME_OVER
}

/** Hasil lemparan dadu (dua dadu). [isDouble] memicu aturan jail/dadu kembar. */
data class DiceRoll(val die1: Int, val die2: Int) {
    val total: Int get() = die1 + die2
    val isDouble: Boolean get() = die1 == die2
}

/**
 * Snapshot state game yang tak berubah (immutable). UI (KorGE) mengamati
 * StateFlow<GameState> dan merender ulang saat snapshot baru diterbitkan.
 */
data class GameState(
    val players: List<Player> = emptyList(),
    val currentPlayerIndex: Int = 0,
    val phase: GamePhase = GamePhase.WAITING_FOR_ROLL,
    val lastRoll: DiceRoll? = null,
    val tiles: List<Tile> = emptyList(),
    val tileOwners: Map<Int, Int> = emptyMap(),
    val message: String = ""
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val isGameOver: Boolean get() = players.count { !it.isBankrupt } <= 1
}
