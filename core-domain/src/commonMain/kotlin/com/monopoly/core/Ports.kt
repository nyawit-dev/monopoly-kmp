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
    fun updateUI(state: GameState)
}

/**
 * Port pemuat aset. core-assets mengimplementasikan: memuat board_config.json
 * dari resources commonMain sehingga mengubah harga properti tak perlu recompile.
 */
interface AssetLoaderPort {
    suspend fun loadBoardConfig(): List<Tile>
}
