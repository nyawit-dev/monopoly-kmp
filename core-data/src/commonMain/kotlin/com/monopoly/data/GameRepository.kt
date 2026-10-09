package com.monopoly.data

import com.monopoly.core.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sumber kebenaran tunggal state game. Memakai MutableStateFlow agar UI
 * mengamati snapshot immutable dan tak pernah terjadi race condition
 * (input dikunci lewat GamePhase di engine, bukan di sini).
 */
class GameRepository(initialTiles: List<com.monopoly.core.Tile> = emptyList()) {

    private val _state = MutableStateFlow(GameState(tiles = initialTiles))
    val state: StateFlow<GameState> = _state.asStateFlow()

    fun update(transform: (GameState) -> GameState) {
        _state.value = transform(_state.value)
    }
}
