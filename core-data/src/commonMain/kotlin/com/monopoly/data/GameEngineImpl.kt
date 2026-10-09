package com.monopoly.data

import com.monopoly.core.DiceRoll
import com.monopoly.core.GameEnginePort
import com.monopoly.core.GamePhase
import com.monopoly.core.GameRendererPort
import com.monopoly.core.GameState
import com.monopoly.core.Player
import com.monopoly.core.TileType
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mesin aturan Monopoli. Murni KMP, tanpa referensi KorGE/platform.
 *
 * Alur satu giliran (sesuai cetak biru, diperluas dengan aturan lengkap):
 *  1. Cek fase (anti double-click).
 *  2. Lempar dadu, animasi lewat renderer.
 *  3. Gerakkan pion (lewat START -> +GO salary).
 *  4. Resolve petak: beli / bayar sewa / event.
 *  5. Cek kebangkrutan, ganti giliran.
 */
class GameEngineImpl(
    private val repo: GameRepository,
    private val renderer: GameRendererPort,
    private val random: Random = Random.Default
) : GameEnginePort {

    override val state: GameState get() = repo.state.value

    override fun observeState() = repo.state

    override fun startGame(playerNames: List<String>) {
        val colors = listOf("#C8102E", "#1B4F9C", "#2E7D32", "#F9A825", "#6A1B9A", "#00838F")
        val players = playerNames.mapIndexed { i, name ->
            Player(id = i, name = name, colorHex = colors[i % colors.size])
        }
        repo.update {
            GameState(
                players = players,
                currentPlayerIndex = 0,
                phase = GamePhase.WAITING_FOR_ROLL,
                tiles = it.tiles,
                tileOwners = emptyMap(),
                message = "Giliran ${players.first().name}. Lempar dadu!"
            )
        }
        renderer.updateUI(repo.state.value)
    }

    override suspend fun rollDiceAndMove() {
        val s = state
        if (s.phase != GamePhase.WAITING_FOR_ROLL) return // Anti double-click
        if (s.isGameOver) return

        val player = s.currentPlayer

        // Pemain di penjara: coba lepas dengan dadu kembar, atau tunggu 3 giliran.
        if (player.inJail) {
            handleJailTurn(player)
            return
        }

        // 1. Roll
        repo.update { it.copy(phase = GamePhase.ROLLING) }
        val roll = DiceRoll(random.nextInt(1, 7), random.nextInt(1, 7))
        renderer.animateDiceRoll(roll)
        delay(450)

        // 2. Move
        repo.update { it.copy(phase = GamePhase.MOVING, lastRoll = roll) }
        val from = player.position
        val boardSize = state.tiles.size.coerceAtLeast(40)
        val passedStart = from + roll.total >= boardSize
        val to = (from + roll.total) % boardSize
        player.position = to
        if (passedStart) {
            player.money += Player.GO_SALARY
        }
        renderer.animatePionMove(player.id, from, to)
        delay(120)

        // 3. Resolve landing
        repo.update { it.copy(phase = GamePhase.RESOLVING_ACTION) }
        resolveLanding(player, roll)

        // 4. Next turn / bankruptcy
        checkBankruptcy(player)
        if (state.isGameOver) {
            repo.update { it.copy(phase = GamePhase.GAME_OVER, message = "Game selesai!") }
            renderer.updateUI(repo.state.value)
            return
        }
        advanceTurn(roll)
    }

    /** Tangani giliran pemain yang sedang dipenjara. */
    private suspend fun handleJailTurn(player: Player) {
        val roll = DiceRoll(random.nextInt(1, 7), random.nextInt(1, 7))
        renderer.animateDiceRoll(roll)
        delay(450)
        if (roll.isDouble) {
            player.inJail = false
            player.jailTurns = 0
            player.position = (player.position + roll.total) % state.tiles.size.coerceAtLeast(40)
            repo.update {
                it.copy(lastRoll = roll, message = "${player.name} keluar penjara dengan dadu kembar!")
            }
            renderer.animatePionMove(player.id, player.position, player.position)
            resolveLanding(player, roll)
        } else {
            player.jailTurns += 1
            if (player.jailTurns >= 3) {
                player.jailTurns = 0
                player.money -= JAIL_FINE
                player.inJail = false
                repo.update { it.copy(lastRoll = roll, message = "${player.name} bayar denda $JAIL_FINE, keluar penjara.") }
            } else {
                repo.update { it.copy(lastRoll = roll, message = "${player.name} tetap di penjara (${player.jailTurns}/3).") }
            }
        }
        renderer.updateUI(repo.state.value)
        advanceTurn(roll)
    }

    /** Terapkan aturan petak tempat pemain mendarat. */
    private suspend fun resolveLanding(player: Player, roll: DiceRoll) {
        val tile = state.tiles.getOrNull(player.position)
        if (tile == null || tile.isCorner) {
            // Petak sudut: START/JAIL/FREE_PARKING/GO_TO_JAIL.
            repo.update { it.copy(message = cornerMessage(player, tile?.name)) }
            renderer.updateUI(repo.state.value)
            return
        }

        val ownerId = state.tileOwners[tile.index]
        when {
            ownerId == null -> {
                // Belum ada pemilik: tawarkan beli (jika cukup uang).
                if (player.money >= tile.price && tile.price > 0) {
                    val buy = renderer.showBuyDialog(tile.index)
                    if (buy) buyProperty(player, tile.index, tile.price)
                    else repo.update { it.copy(message = "${player.name} melewati ${tile.name}.") }
                } else {
                    repo.update { it.copy(message = "${tile.name} tak terjangkau untuk ${player.name}.") }
                }
            }
            ownerId == player.id -> {
                repo.update { it.copy(message = "${player.name} mendarat di propertinya sendiri.") }
            }
            else -> {
                // Milik pemain lain: bayar sewa.
                val owner = state.players[ownerId]
                val rent = computeRent(tile.index)
                payRent(player, owner, rent, tile.name)
            }
        }
        renderer.updateUI(repo.state.value)
    }

    override suspend fun buyCurrentProperty() {
        val player = state.currentPlayer
        val tile = state.tiles.getOrNull(player.position) ?: return
        if (state.tileOwners.containsKey(tile.index)) return
        if (player.money < tile.price) return
        buyProperty(player, tile.index, tile.price)
        renderer.updateUI(repo.state.value)
    }

    private fun buyProperty(player: Player, tileIndex: Int, price: Int) {
        player.money -= price
        player.ownedProperties.add(tileIndex)
        repo.update { s ->
            s.copy(
                tileOwners = s.tileOwners + (tileIndex to player.id),
                message = "${player.name} membeli ${s.tiles[tileIndex].name} seharga $price."
            )
        }
    }

    override suspend fun buildHouse(tileIndex: Int) {
        val player = state.currentPlayer
        val tile = state.tiles.getOrNull(tileIndex) ?: return
        if (state.tileOwners[tileIndex] != player.id || tile.type != TileType.PROPERTY) return
        if (player.money < tile.housePrice) return
        player.money -= tile.housePrice
        repo.update { it.copy(message = "${player.name} membangun rumah di ${tile.name}.") }
        renderer.updateUI(repo.state.value)
    }

    private fun computeRent(tileIndex: Int): Int {
        val tile = state.tiles[tileIndex]
        // Monopoli warna: sewa dasar x2 bila pemilik punya seluruh grup warna.
        val group = tile.colorGroup ?: return tile.baseRent
        val ownerId = state.tileOwners[tileIndex] ?: return tile.baseRent
        val ownsAll = state.tiles.filter { it.colorGroup == group }.all { state.tileOwners[it.index] == ownerId }
        return if (ownsAll) tile.baseRent * 2 else tile.baseRent
    }

    private fun payRent(from: Player, to: Player, rent: Int, tileName: String) {
        val paid = minOf(from.money, rent)
        from.money -= paid
        to.money += paid
        repo.update {
            it.copy(message = "${from.name} bayar sewa $paid ke ${to.name} di $tileName.")
        }
    }

    private fun checkBankruptcy(player: Player) {
        if (player.money < 0) {
            player.isBankrupt = true
            // Lepas semua properti miliknya.
            repo.update { s ->
                s.copy(tileOwners = s.tileOwners.filterValues { it != player.id })
            }
        }
    }

    private fun advanceTurn(roll: DiceRoll) {
        repo.update { s ->
            if (roll.isDouble && !s.currentPlayer.inJail) {
                // Dadu kembar: pemain utama boleh lempar lagi.
                s.copy(phase = GamePhase.WAITING_FOR_ROLL, message = "${s.currentPlayer.name} dadu kembar, lempar lagi!")
            } else {
                val next = nextActiveIndex(s)
                s.copy(currentPlayerIndex = next, phase = GamePhase.WAITING_FOR_ROLL,
                    message = "Giliran ${s.players[next].name}.")
            }
        }
        renderer.updateUI(repo.state.value)
    }

    private fun nextActiveIndex(s: GameState): Int {
        var idx = (s.currentPlayerIndex + 1) % s.players.size
        while (s.players[idx].isBankrupt && idx != s.currentPlayerIndex) {
            idx = (idx + 1) % s.players.size
        }
        return idx
    }

    private fun cornerMessage(player: Player, name: String?): String = when (name) {
        "START", "GO" -> "${player.name} mendarat di START."
        "JAIL" -> "${player.name} hanya berkunjung ke penjara."
        "FREE_PARKING" -> "${player.name} di Free Parking."
        "GO_TO_JAIL" -> "${player.name} masuk penjara!"
        else -> "${player.name} di ${name ?: "petak sudut"}."
    }

    companion object {
        const val JAIL_FINE = 50
    }
}
