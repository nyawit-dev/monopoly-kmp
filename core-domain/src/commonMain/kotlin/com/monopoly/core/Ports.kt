package com.monopoly.core

/**
 * Port mesin game. core-data mengimplementasikan; dipanggil dari UI/app.
 * Seluruh aturan Monopoli hidup di balik interface ini, murni KMP.
 */
interface GameEnginePort {
    /** State terkini (snapshot immutable untuk dirender UI). */
    val state: GameState

    /** Flow state agar UI bisa observe perubahan (KorGE collect). */
    fun observeState(): kotlinx.coroutines.flow.StateFlow<GameState>

    /** Lempar dadu lalu gerakkan pion, resolve petak, akhiri giliran. */
    suspend fun rollDiceAndMove()

    /** Mulai game baru dengan daftar pemain. */
    fun startGame(playerNames: List<String>)

    /** Beli properti di posisi pemain saat ini (dipanggil UI setelah dialog). */
    suspend fun buyCurrentProperty()

    /** Bangun rumah pada properti milik pemain saat ini. */
    suspend fun buildHouse(tileIndex: Int)

    /** Jual rumah (kembalikan setengah harga bangunan) dari properti pemain. */
    suspend fun sellHouse(tileIndex: Int)

    /** Hipotik properti: terima nilai hipotik, properti berhenti menghasilkan sewa. */
    suspend fun mortgageProperty(tileIndex: Int)

    /** Bebaskan hipotik: bayar nilai hipotik + 10% biaya, properti aktif lagi. */
    suspend fun unmortgageProperty(tileIndex: Int)

    /** Bayar denda $50 untuk keluar penjara di awal giliran (opsional). */
    suspend fun payJailFine()

    /**
     * Muat ulang aturan Lua dari disk tanpa restart game (user mengedit
     * rules.lua lalu menekan "Muat Ulang Aturan" di UI). true bila berhasil.
     */
    fun reloadRules(): Boolean

    /** Sumber aturan aktif: path file rules.lua user, atau "bawaan". */
    val ruleSourceName: String
}

/**
 * Port renderer. ui-korge mengimplementasikan. KorGE TIDAK tahu aturan game;
 * ia hanya menjalankan perintah animasi/visual yang diminta mesin.
 */
interface GameRendererPort {
    suspend fun animateDiceRoll(finalValue: DiceRoll)
    suspend fun animatePionMove(playerId: Int, fromPosition: Int, toPosition: Int)
    suspend fun showBuyDialog(tileIndex: Int): Boolean
    suspend fun showMessage(text: String)

    /**
     * Tampilkan kartu Chance/Community Chest; menunggu pemain menutupnya.
     * Dipakai mesin saat resolve petak EVENT.
     */
    suspend fun showEventCard(text: String)

    fun updateUI(state: GameState)
}

/**
 * Port pemuat aset. core-assets mengimplementasikan: memuat board_config.json
 * dari resources commonMain sehingga mengubah harga properti tak perlu recompile.
 */
interface AssetLoaderPort {
    suspend fun loadBoardConfig(): List<Tile>
}
