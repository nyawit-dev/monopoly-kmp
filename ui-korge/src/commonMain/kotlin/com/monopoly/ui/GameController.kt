package com.monopoly.ui

import com.monopoly.core.GameEnginePort
import com.monopoly.core.GameState
import com.monopoly.core.Tile
import com.monopoly.data.toGameState
import com.monopoly.net.Actions
import com.monopoly.net.GameSession
import com.monopoly.net.Phases
import com.monopoly.net.SnapshotDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pengendali permainan untuk UI (cetak biru dokumen 12): GameplayScene
 * hanya bicara ke interface ini — sama untuk main lokal maupun jaringan.
 */
interface GameController {
    val state: GameState
    fun observeState(): StateFlow<GameState>

    /** true bila perangkat ini boleh beraksi sekarang. */
    val isMyTurn: Boolean

    suspend fun rollDiceAndMove()
    suspend fun buyCurrentProperty()
    suspend fun buildHouse(tileIndex: Int)
    suspend fun sellHouse(tileIndex: Int)
    suspend fun mortgageProperty(tileIndex: Int)
    suspend fun unmortgageProperty(tileIndex: Int)
    suspend fun payJailFine()

    fun reloadRules(): Boolean
    val ruleSourceName: String
}

/** Main lokal: delegasi langsung ke mesin game. */
class LocalController(private val engine: GameEnginePort) : GameController {
    override val state: GameState get() = engine.state
    override fun observeState(): StateFlow<GameState> = engine.observeState()
    override val isMyTurn: Boolean get() = true
    override suspend fun rollDiceAndMove() = engine.rollDiceAndMove()
    override suspend fun buyCurrentProperty() = engine.buyCurrentProperty()
    override suspend fun buildHouse(tileIndex: Int) = engine.buildHouse(tileIndex)
    override suspend fun sellHouse(tileIndex: Int) = engine.sellHouse(tileIndex)
    override suspend fun mortgageProperty(tileIndex: Int) = engine.mortgageProperty(tileIndex)
    override suspend fun unmortgageProperty(tileIndex: Int) = engine.unmortgageProperty(tileIndex)
    override suspend fun payJailFine() = engine.payJailFine()
    override fun reloadRules(): Boolean = engine.reloadRules()
    override val ruleSourceName: String get() = engine.ruleSourceName
}

/**
 * Guest jaringan (cetak biru dokumen 12): aksi dikirim sebagai Command ke
 * host; state direkonstruksi dari SnapshotDto/delta. [tiles] dimuat lokal
 * (RealBoardLoader) sebelum controller dibuat. Giliran diperiksa lewat
 * myIndex sesi vs currentPlayerIndex state.
 */
class NetworkController(
    private val session: GameSession,
    tiles: List<Tile>
) : GameController {

    private val tiles = tiles

    private val _state = MutableStateFlow(
        SnapshotDto(
            players = emptyList(),
            tiles = emptyList(),
            currentPlayerIndex = 0,
            phase = Phases.WAITING_FOR_ROLL
        ).toGameState(tiles)
    )

    override val state: GameState get() = _state.value
    override fun observeState(): StateFlow<GameState> = _state

    /** Indeks pemain lokal di sesi (dari Welcome host). */
    val myIndex: StateFlow<Int?> = session.myIndex

    override val isMyTurn: Boolean
        get() = session.myIndex.value != null && _state.value.currentPlayerIndex == session.myIndex.value

    override suspend fun rollDiceAndMove() = session.sendCommand(Actions.ROLL)
    override suspend fun buyCurrentProperty() = session.sendCommand(Actions.BUY)
    override suspend fun buildHouse(tileIndex: Int) = session.sendCommand(Actions.BUILD_HOUSE, tileIndex)
    override suspend fun sellHouse(tileIndex: Int) = session.sendCommand(Actions.SELL_HOUSE, tileIndex)
    override suspend fun mortgageProperty(tileIndex: Int) = session.sendCommand(Actions.MORTGAGE, tileIndex)
    override suspend fun unmortgageProperty(tileIndex: Int) = session.sendCommand(Actions.UNMORTGAGE, tileIndex)
    override suspend fun payJailFine() = session.sendCommand(Actions.PAY_FINE)

    override fun reloadRules(): Boolean = false
    override val ruleSourceName: String get() = "host"

    /**
     * Pantau state sesi, terjemahkan ke GameState (delta sudah ditambal
     * GameSession). Panggil sekali di scene (collect tak pernah balik).
     */
    suspend fun pumpState() {
        session.state.collect { dto -> if (dto != null) _state.value = dto.toGameState(tiles) }
    }
}
