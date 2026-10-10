package com.monopoly.core

import kotlinx.serialization.Serializable

/**
 * Tipe petak papan. Menentukan aturan saat pemain mendarat.
 * Dipisah sebagai enum agar core-domain tidak bergantung dari parser JSON.
 */
enum class TileType {
    CORNER,      // START, JAIL, FREE_PARKING, GO_TO_JAIL
    PROPERTY,    // Properti yang bisa dibeli, dibangun rumah, & disewakan
    RAILROAD,    // Stasiun (sewa 25 x 2^(jumlah dimiliki - 1))
    UTILITY,     // Listrik/Air (sewa 4x atau 10x angka dadu)
    EVENT        // Chance / Community Chest / Tax
}

/**
 * Petak papan. [colorGroup], [rentSchedule] (6 nilai: dasar, 1-4 rumah,
 * hotel), [housePrice] & [mortgageValue] hanya relevan untuk PROPERTY;
 * [taxAmount] untuk petak pajak. Semua nullable/0 agar CORNER tetap ringan.
 */
@Serializable
data class Tile(
    val index: Int,
    val name: String,
    val type: TileType,
    val price: Int = 0,
    val colorGroup: String? = null,
    val baseRent: Int = 0,
    /** [dasar, 1 rumah, 2 rumah, 3 rumah, 4 rumah, hotel]. */
    val rentSchedule: List<Int> = emptyList(),
    val housePrice: Int = 0,
    val mortgageValue: Int = 0,
    /** Jumlah pajak (Income Tax / Luxury Tax); 0 bila bukan petak pajak. */
    val taxAmount: Int = 0,
    val isCorner: Boolean = type == TileType.CORNER,
    val isStart: Boolean = false
) {
    /** Sewa dengan [houses] rumah (0..5, 5 = hotel); grup penuh = sewa dasar x2. */
    fun rentFor(houses: Int, ownsFullGroup: Boolean): Int {
        if (type == TileType.PROPERTY && rentSchedule.size >= 6) {
            return if (houses > 0) rentSchedule[houses.coerceIn(1, 5)]
                   else if (ownsFullGroup) rentSchedule[0] * 2
                   else rentSchedule[0]
        }
        return baseRent
    }
}

/**
 * Pemain. IMMUTABLE: setiap perubahan uang/posisi membuat salinan baru
 * lewat GameState, sehingga StateFlow selalu memancarkan snapshot valid
 * dan UI tak pernah membaca objek yang dimutasi diam-diam.
 */
data class Player(
    val id: Int,
    val name: String,
    val colorHex: String,
    val money: Int = STARTING_CASH,
    val position: Int = 0,
    val inJail: Boolean = false,
    val jailTurns: Int = 0,
    val isBankrupt: Boolean = false,
    val ownedProperties: Set<Int> = emptySet(),
    /** Jumlah kartu "Bebas Penjara" yang dipegang. */
    val jailCards: Int = 0
) {
    companion object {
        const val STARTING_CASH = 1500
        const val GO_SALARY = 200
        const val JAIL_FINE = 50
        const val MAX_HOUSES = 5 // 5 = hotel
        const val JAIL_POSITION = 10
    }
}

/** Fase giliran. Mengunci input UI agar tak ada aksi ganda (anti race). */
enum class GamePhase {
    WAITING_FOR_ROLL,
    ROLLING,
    MOVING,
    RESOLVING_ACTION,
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
    /** Jumlah rumah per petak (0..5; 5 = hotel). */
    val houses: Map<Int, Int> = emptyMap(),
    /** Petak yang dihipotik (tak menghasilkan sewa sampai dibebaskan). */
    val mortgaged: Set<Int> = emptySet(),
    /** Jumlah dadu kembar berturut-turut (3 -> penjara). */
    val consecutiveDoubles: Int = 0,
    val message: String = "",
    /** Teks kartu Chance/Chest terbaru untuk ditampilkan UI; null bila tak ada. */
    val eventCard: String? = null,
    /** Nomor giliran (naik tiap nextTurn); dipakai sesi jaringan (delta). */
    val turnNumber: Int = 0
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]

    val isGameOver: Boolean get() = players.count { !it.isBankrupt } <= 1

    /** Pemenang (pemain non-bangkrut terakhir); null bila belum selesai. */
    val winner: Player? get() = players.filter { !it.isBankrupt }.takeIf { it.size == 1 }?.first()

    /** Kekayaan bersih pemain: uang + nilai properti (harga + rumah). */
    fun netWorth(player: Player): Int {
        val props = player.ownedProperties.sumOf { idx ->
            val t = tiles.getOrNull(idx) ?: return@sumOf 0
            val houseCost = (houses[idx] ?: 0) * t.housePrice
            if (idx in mortgaged) t.mortgageValue else t.price + houseCost
        }
        return player.money + props
    }
}
