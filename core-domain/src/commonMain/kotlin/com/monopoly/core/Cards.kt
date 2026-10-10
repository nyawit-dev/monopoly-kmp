package com.monopoly.core

import kotlinx.serialization.Serializable

/**
 * Kartu Chance / Community Chest. Deck standar Monopoli; efek diterjemahkan
 * ke aksi engine (diproses GameEngineImpl). [goTo] = petak tujuan (absolut),
 * [backSpaces] = mundur N petak, [amount] = nominal uang, [goToJail] memicu
 * aturan penjara.
 */
@Serializable
data class GameCard(
    val deck: Deck,
    val text: String,
    val goTo: Int? = null,
    val backSpaces: Int = 0,
    val amount: Int = 0,
    val goToJail: Boolean = false,
    /** Ambil kartu "Bebas Penjara" (disimpan pemain, bisa dipakai sekali). */
    val jailFree: Boolean = false,
    /** Bayar tiap pemain lain (Grand Opera Night); negatif = terima dari tiap pemain. */
    val perPlayerAmount: Int = 0,
    /** Kartu perbaikan: bayar per rumah & per hotel (0 = bukan kartu perbaikan). */
    val perHouseAmount: Int = 0,
    val perHotelAmount: Int = 0
) {
    enum class Deck { CHANCE, CHEST }
}

/**
 * Deck standar. Dikocok per permainan oleh engine (draw dari atas, buang ke
 * bawah, kosong -> kocok ulang). Chance & Chest dipisah.
 */
object StandardDecks {

    val CHANCE: List<GameCard> = listOf(
        GameCard(GameCard.Deck.CHANCE, "Maju ke START (terima $200)", goTo = 0),
        GameCard(GameCard.Deck.CHANCE, "Maju ke Illinois Avenue", goTo = 24),
        GameCard(GameCard.Deck.CHANCE, "Maju ke St. Charles Place", goTo = 11),
        GameCard(GameCard.Deck.CHANCE, "Bank membayar dividen $50", amount = 50),
        GameCard(GameCard.Deck.CHANCE, "Kartu Bebas Penjara — simpan untuk dipakai", jailFree = true),
        GameCard(GameCard.Deck.CHANCE, "Mundur 3 petak", backSpaces = 3),
        GameCard(GameCard.Deck.CHANCE, "Langsung ke Penjara — jangan lewat START", goToJail = true),
        GameCard(GameCard.Deck.CHANCE, "Perbaiki properti: $25/rumah, $100/hotel", perHouseAmount = 25, perHotelAmount = 100),
        GameCard(GameCard.Deck.CHANCE, "Bayar pajak miskin $15", amount = -15),
        GameCard(GameCard.Deck.CHANCE, "Naik Reading Railroad", goTo = 5),
        GameCard(GameCard.Deck.CHANCE, "Jalan ke Boardwalk", goTo = 39),
        GameCard(GameCard.Deck.CHANCE, "Terpilih Ketua Dewan: bayar tiap pemain $50", perPlayerAmount = -50),
        GameCard(GameCard.Deck.CHANCE, "Pinjaman bangunan jatuh tempo, terima $150", amount = 150),
        GameCard(GameCard.Deck.CHANCE, "Menang lomba teka-teki, terima $100", amount = 100)
    )

    val CHEST: List<GameCard> = listOf(
        GameCard(GameCard.Deck.CHEST, "Maju ke START (terima $200)", goTo = 0),
        GameCard(GameCard.Deck.CHEST, "Salah bank, untung $200", amount = 200),
        GameCard(GameCard.Deck.CHEST, "Bayar biaya dokter $50", amount = -50),
        GameCard(GameCard.Deck.CHEST, "Jual saham, terima $50", amount = 50),
        GameCard(GameCard.Deck.CHEST, "Kartu Bebas Penjara — simpan untuk dipakai", jailFree = true),
        GameCard(GameCard.Deck.CHEST, "Langsung ke Penjara — jangan lewat START", goToJail = true),
        GameCard(GameCard.Deck.CHEST, "Malam Opera: terima $50 dari tiap pemain", perPlayerAmount = 50),
        GameCard(GameCard.Deck.CHEST, "Dana liburan jatuh tempo, terima $100", amount = 100),
        GameCard(GameCard.Deck.CHEST, "Pengembalian pajak, terima $20", amount = 20),
        GameCard(GameCard.Deck.CHEST, "Ulang tahunmu! Terima $10 dari tiap pemain", perPlayerAmount = 10),
        GameCard(GameCard.Deck.CHEST, "Asuransi jiwa jatuh tempo, terima $100", amount = 100),
        GameCard(GameCard.Deck.CHEST, "Bayar biaya rumah sakit $100", amount = -100),
        GameCard(GameCard.Deck.CHEST, "Bayar biaya sekolah $50", amount = -50),
        GameCard(GameCard.Deck.CHEST, "Terima konsultasi $25", amount = 25),
        GameCard(GameCard.Deck.CHEST, "Perbaikan jalan: $40/rumah, $115/hotel", perHouseAmount = 40, perHotelAmount = 115),
        GameCard(GameCard.Deck.CHEST, "Juara dua kontes kecantikan, terima $10", amount = 10),
        GameCard(GameCard.Deck.CHEST, "Waris $100", amount = 100)
    )
}
