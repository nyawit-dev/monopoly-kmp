package com.monopoly.data

import com.monopoly.core.DiceRoll
import com.monopoly.core.GamePhase
import com.monopoly.core.GameState
import com.monopoly.core.Player
import com.monopoly.core.Tile
import com.monopoly.net.DiceDto
import com.monopoly.net.PlayerDto
import com.monopoly.net.SnapshotDelta
import com.monopoly.net.SnapshotDto
import com.monopoly.net.TileDto

/**
 * Jembatan engine ↔ sesi jaringan (cetak biru dokumen 12): konversi
 * state game ke DTO kabel, dan pembuat delta hemat bandwidth.
 */
fun GameState.toSnapshotDto(): SnapshotDto = SnapshotDto(
    players = players.map { p ->
        PlayerDto(
            name = p.name,
            money = p.money,
            position = p.position,
            inJail = p.inJail,
            jailTurns = p.jailTurns,
            jailCards = p.jailCards,
            properties = p.ownedProperties.toList(),
            isBankrupt = p.isBankrupt
        )
    },
    tiles = tiles.map { t ->
        TileDto(
            index = t.index,
            ownerName = tileOwners[t.index]?.let { players[it].name },
            houses = houses[t.index] ?: 0,
            mortgaged = t.index in mortgaged
        )
    },
    currentPlayerIndex = currentPlayerIndex,
    phase = phase.name,
    message = message,
    lastRoll = lastRoll?.let { DiceDto(it.die1, it.die2, it.total, it.isDouble) },
    eventCard = eventCard,
    consecutiveDoubles = consecutiveDoubles,
    turnNumber = turnNumber
)

/**
 * Delta dari [this] (state lama) ke [other] (state baru): hanya field
 * yang berubah ikut. [since] = turnNumber state lama (basis guest).
 */
fun SnapshotDto.diffTo(other: SnapshotDto, since: Int): SnapshotDelta {
    val players = HashMap<Int, PlayerDto?>()
    other.players.forEachIndexed { i, p ->
        if (this.players.getOrNull(i) != p) players[i] = p
    }
    val tiles = HashMap<Int, TileDto>()
    other.tiles.forEach { t ->
        if (this.tiles.firstOrNull { it.index == t.index } != t) tiles[t.index] = t
    }
    return SnapshotDelta(
        currentPlayerIndex = if (currentPlayerIndex != other.currentPlayerIndex) other.currentPlayerIndex else null,
        phase = if (phase != other.phase) other.phase else null,
        message = if (message != other.message) other.message else null,
        lastRoll = if (lastRoll != other.lastRoll) other.lastRoll else null,
        eventCard = if (eventCard != other.eventCard) other.eventCard else null,
        consecutiveDoubles = if (consecutiveDoubles != other.consecutiveDoubles) other.consecutiveDoubles else null,
        turnNumber = other.turnNumber,
        players = players,
        tiles = tiles
    )
}

private val PLAYER_COLORS = listOf(
    "#C8102E", "#1B4F9C", "#2E7D32", "#F9A825", "#6A1B9A", "#00838F"
)

/**
 * Snapshot kabel → GameState untuk guest (cetak biru dokumen 12):
 * papan [tiles] dimuat lokal (RealBoardLoader), sisanya dari DTO.
 */
fun SnapshotDto.toGameState(tiles: List<Tile>): GameState {
    val players = this.players.mapIndexed { i, p ->
        Player(
            id = i,
            name = p.name,
            colorHex = PLAYER_COLORS[i % PLAYER_COLORS.size],
            money = p.money,
            position = p.position,
            inJail = p.inJail,
            jailTurns = p.jailTurns,
            isBankrupt = p.isBankrupt,
            ownedProperties = p.properties.toSet(),
            jailCards = p.jailCards
        )
    }
    val tileOwners = this.tiles.mapNotNull { t ->
        val owner = t.ownerName?.let { name -> players.indexOfFirst { it.name == name } }
        if (owner != null && owner >= 0) t.index to owner else null
    }.toMap()
    return GameState(
        players = players,
        tiles = tiles,
        currentPlayerIndex = currentPlayerIndex,
        tileOwners = tileOwners,
        houses = this.tiles.filter { it.houses > 0 }.associate { it.index to it.houses },
        mortgaged = this.tiles.filter { it.mortgaged }.map { it.index }.toSet(),
        message = message ?: "",
        lastRoll = lastRoll?.let { DiceRoll(it.d1, it.d2) },
        eventCard = eventCard,
        consecutiveDoubles = consecutiveDoubles,
        turnNumber = turnNumber,
        phase = runCatching { GamePhase.valueOf(phase) }.getOrDefault(GamePhase.WAITING_FOR_ROLL)
    )
}
