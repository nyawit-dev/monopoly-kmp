package com.monopoly.core

/**
 * Snapshot pemain yang diteruskan ke handler mod (read-only).
 */
data class PlayerSnapshot(
    val name: String,
    val money: Int,
    val position: Int,
    val inJail: Boolean,
    val properties: List<Int> = emptyList()
)

/**
 * Snapshot petak yang diteruskan ke handler mod (read-only).
 */
data class TileSnapshot(
    val index: Int,
    val name: String,
    val type: String,
    val price: Int,
    val colorGroup: String?,
    val owner: String?
)

/**
 * Snapshot lemparan dadu yang diteruskan ke handler mod.
 */
data class DiceSnapshot(
    val d1: Int,
    val d2: Int,
    val total: Int,
    val isDouble: Boolean
)

/**
 * Konteks event game. Field yang tak relevan untuk suatu event bernilai
 * null — handler mod hanya membaca yang dibutuhkan. Spesifikasi lengkap:
 * docs/cetak-biru/04-gerbang-m.md.
 */
data class EventContext(
    val player: PlayerSnapshot,
    val tile: TileSnapshot? = null,
    val dice: DiceSnapshot? = null,
    val consecutiveDoubles: Int = 0,
    val from: Int? = null,
    val to: Int? = null,
    val deck: String? = null,
    val cardText: String? = null,
    val reason: String? = null,
    val creditor: String? = null,
    val winner: String? = null,
    val netWorth: Int? = null,
    val price: Int? = null,
    val houseCount: Int? = null,
    val turnNumber: Int? = null
)

/**
 * Event game yang bisa didengar mod lewat `M.on`. Setiap event punya aturan
 * field ctx yang dibaca (lihat cetak biru dokumen 04 §2).
 */
sealed class GameEvent {
    object TurnStart : GameEvent()
    object Roll : GameEvent()
    object Land : GameEvent()
    object PassStart : GameEvent()
    object CardDrawn : GameEvent()
    object PropertyBought : GameEvent()
    object HouseBuilt : GameEvent()
    object JailEnter : GameEvent()
    object JailExit : GameEvent()
    object Bankrupt : GameEvent()
    object GameOver : GameEvent()

    /** Nama event untuk Lua (`M.on("land", ...)`). */
    val key: String
        get() = when (this) {
            TurnStart -> "turn_start"; Roll -> "roll"; Land -> "land"
            PassStart -> "pass_start"; CardDrawn -> "card_drawn"
            PropertyBought -> "property_bought"; HouseBuilt -> "house_built"
            JailEnter -> "jail_enter"; JailExit -> "jail_exit"
            Bankrupt -> "bankrupt"; GameOver -> "game_over"
        }

    companion object {
        fun fromKey(key: String): GameEvent? = when (key) {
            "turn_start" -> TurnStart; "roll" -> Roll; "land" -> Land
            "pass_start" -> PassStart; "card_drawn" -> CardDrawn
            "property_bought" -> PropertyBought; "house_built" -> HouseBuilt
            "jail_enter" -> JailEnter; "jail_exit" -> JailExit
            "bankrupt" -> Bankrupt; "game_over" -> GameOver
            else -> null
        }
    }
}

/**
 * Pemasok state game untuk mod (M.getState / M.getTile). Engine memasangnya
 * ke RuleEngine supaya handler mod bisa membaca snapshot saat dispatch —
 * tanpa pernah menulis state langsung.
 */
interface StateProvider {
    fun players(): List<PlayerSnapshot>
    fun tiles(): List<TileSnapshot>
    fun currentPlayerName(): String
    fun turnNumber(): Int
}

/**
 * Hasil override dari handler mod. Disatukan oleh event bus sesuai semantik
 * merge cetak biru dokumen 04 §4: money_delta aditif, message last-wins,
 * go_to_jail sticky-OR.
 */
data class RuleOutcome(
    val moneyDelta: Int = 0,
    val message: String? = null,
    val goToJail: Boolean = false
)

/**
 * Aksi imperatif yang dipanggil mod dari handler (`M.giveMoney`, dst.).
 * Dikumpulkan bus saat dispatch, lalu diterapkan engine setelah dispatch
 * selesai — mod tak pernah menyentuh state game langsung.
 */
sealed class ModAction {
    data class Money(val player: String, val delta: Int) : ModAction()
    data class Move(val player: String, val steps: Int) : ModAction()
    data class Jail(val player: String, val enter: Boolean) : ModAction()
    data class GrantCard(val player: String) : ModAction()
    data class Message(val text: String) : ModAction()
    data class DrawCard(val player: String, val deck: String) : ModAction()
}
