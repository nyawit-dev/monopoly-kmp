package com.monopoly.data

import com.monopoly.core.DiceRoll
import com.monopoly.core.DiceSnapshot
import com.monopoly.core.EventContext
import com.monopoly.core.GameCard
import com.monopoly.core.GameEnginePort
import com.monopoly.core.GameEvent
import com.monopoly.core.GamePhase
import com.monopoly.core.GameRendererPort
import com.monopoly.core.GameState
import com.monopoly.core.ModAction
import com.monopoly.core.Player
import com.monopoly.core.PlayerSnapshot
import com.monopoly.core.RuleEngine
import com.monopoly.core.RuleOutcome
import com.monopoly.core.StandardDecks
import com.monopoly.core.StateProvider
import com.monopoly.core.Tile
import com.monopoly.core.TileSnapshot
import com.monopoly.core.TileType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * Mesin aturan Monopoli. Murni KMP, tanpa referensi KorGE/platform.
 *
 * Aturan lengkap yang diterapkan:
 *  - Kembalian gaji $200 saat melewati/mendarat START.
 *  - Dadu kembar: lempar lagi; 3 kembar berturut-turut -> langsung penjara.
 *  - GO_TO_JAIL: posisi 10 + inJail (tidak melewati START).
 *  - Penjara: kelu→ lepas dengan dadu kembar (turn berakhir), bayar denda
 *    $50 lalu lempar (payJailFine), atau paksa bayar setelah 3 giliran.
 *  - Kartu Bebas Penjara otomatis dipakai saat ditahan.
 *  - Sewa: properti dari jadwal 6 tingkat (dasar, 1-4 rumah, hotel; grup
 *    penuh = x2 saat tanpa rumah), stasiun 25/50/100/200 per jumlah dimiliki,
 *    utilitas 4x/10x angka dadu. Properti dihipotik tak menghasilkan sewa.
 *  - Rumah/hotel: wajib punya grup penuh, bangun rata (even build), maks 5
 *    (5 = hotel), jual rumah setengah harga.
 *  - Hipotik: terima nilai hipotik (tak boleh ada rumah di grup), bebaskan
 *    dengan bayar nilai + 10%.
 *  - Kartu Chance & Community Chest lengkap (pindah, uang, penjara, perbaikan).
 *  - Pajak Income Tax ($200) & Luxury Tax ($100) memotong uang.
 *  - Kebangkrutan: aset diserahkan ke kreditur (pemain yang menagih) atau
 *    kembali ke bank; game over saat satu pemain tersisa.
 *
 * Player immutable: setiap perubahan menghasilkan Player baru lewat
 * repo.update sehingga StateFlow selalu memancarkan snapshot valid.
 */
class GameEngineImpl(
    private val repo: GameRepository,
    private val renderer: GameRendererPort,
    private val random: Random = Random.Default,
    private val rules: RuleEngine = createRuleEngine()
) : GameEnginePort {

    /** Pemasok state untuk M.getState/getTile (mod hanya membaca). */
    private val stateProvider = object : StateProvider {
        override fun players(): List<PlayerSnapshot> = state.players.map { snapshot(it) }
        override fun tiles(): List<TileSnapshot> = state.tiles.map { tileSnapshot(it) }
        override fun currentPlayerName(): String = state.currentPlayer.name
        override fun turnNumber(): Int = state.turnNumber
    }

    init {
        rules.attachStateProvider(stateProvider)
    }

    private var chanceDeck: List<GameCard> = emptyList()
    private var chestDeck: List<GameCard> = emptyList()

    /**
     * true bila hook aturan kustom (Lua) baru saja mengatur pesan. nextTurn
     * tak menimpanya sehingga pesan kustom tetap terlihat sampai giliran
     * berikutnya berganti.
     */
    private var hookMessageActive = false

    /** Antrean lemparan paksa untuk test deterministik. */
    internal val forcedRolls: ArrayDeque<DiceRoll> = ArrayDeque()

    // --- Test hooks (internal; dipakai test core-data untuk menyusun skenario) ---

    /** Paksa posisi pemain sebelum lemparan berikutnya (playerId to position). */
    internal var testForcedPosition: Pair<Int, Int>? = null

    /** Paksa pemain aktif berikutnya. */
    internal var testForcedTurn: Int? = null

    /** Paksa kartu pertama yang diambil dari deck (chance / chest). */
    internal var testForcedChance: GameCard? = null
    internal var testForcedChest: GameCard? = null

    /** Beri properti ke pemain tanpa pembayaran (uji sewa/rumah). */
    internal fun testGrantProperty(playerId: Int, tileIndex: Int) {
        repo.update { s ->
            s.copy(
                tileOwners = s.tileOwners + (tileIndex to playerId),
                players = s.players.map {
                    if (it.id == playerId) it.copy(ownedProperties = it.ownedProperties + tileIndex) else it
                }
            )
        }
    }

    override val state: GameState get() = repo.state.value

    override fun observeState() = repo.state

    private val boardSize: Int get() = state.tiles.size.coerceAtLeast(40)

    // ================================================================ mulai

    override fun startGame(playerNames: List<String>) {
        val colors = listOf("#C8102E", "#1B4F9C", "#2E7D32", "#F9A825", "#6A1B9A", "#00838F")
        val cash = rules.registry.startingCash
        val players = playerNames.mapIndexed { i, name ->
            Player(id = i, name = name, colorHex = colors[i % colors.size], money = cash)
        }
        chanceDeck = StandardDecks.CHANCE.shuffled(random)
        chestDeck = StandardDecks.CHEST.shuffled(random)
        repo.update {
            GameState(
                players = players,
                currentPlayerIndex = 0,
                phase = GamePhase.WAITING_FOR_ROLL,
                tiles = it.tiles,
                tileOwners = emptyMap(),
                message = "Giliran ${players.first().name}. Lempar dadu!",
                turnNumber = 0
            )
        }
        renderer.updateUI(state)
    }

    // ============================================================ lempar dadu

    override suspend fun rollDiceAndMove() {
        val s = state
        if (s.phase != GamePhase.WAITING_FOR_ROLL) return // Anti double-click
        if (s.isGameOver) return
        hookMessageActive = false // reset tiap giliran baru

        // Test hooks: paksa giliran/posisi.
        testForcedTurn?.let { t ->
            repo.update { it.copy(currentPlayerIndex = t) }
            testForcedTurn = null
        }
        testForcedPosition?.let { (pid, pos) ->
            modifyPlayer(pid) { it.copy(position = pos) }
            testForcedPosition = null
        }

        val player = state.currentPlayer
        emit(GameEvent.TurnStart, player)
        repo.update { it.copy(phase = GamePhase.ROLLING) }

        val roll = rollDice()
        renderer.animateDiceRoll(roll)
        delay(450)
        repo.update { it.copy(lastRoll = roll) }
        emit(GameEvent.Roll, player, dice = roll)

        // ---- Pemain di penjara: giliran khusus ----
        if (player.inJail) {
            handleJailRoll(player, roll)
            return
        }

        // ---- 3 dadu kembar berturut-turut -> penjara ----
        val doubles = if (roll.isDouble) s.consecutiveDoubles + 1 else 0
        repo.update { it.copy(consecutiveDoubles = doubles) }
        if (doubles >= 3 && rules.registry.threeDoublesToJail) {
            sendToJail(player)
            nextTurn()
            return
        }

        // ---- Bergerak ----
        repo.update { it.copy(phase = GamePhase.MOVING) }
        movePlayer(player, roll.total)
        delay(120)

        // Mendarat di GO_TO_JAIL? (dikirim penjara tanpa lewat START)
        if (state.players[player.id].inJail) {
            nextTurn()
            return
        }

        repo.update { it.copy(phase = GamePhase.RESOLVING_ACTION) }
        resolveLanding(state.players[player.id], roll)

        checkBankruptcy()
        if (state.isGameOver) { gameOver(); return }

        if (roll.isDouble && !state.currentPlayer.inJail) {
            repo.update {
                it.copy(
                    phase = GamePhase.WAITING_FOR_ROLL,
                    // Pesan kustom dari hook aturan tidak ditimpa.
                    message = if (hookMessageActive) it.message
                    else "${it.currentPlayer.name} dadu kembar, lempar lagi!"
                )
            }
            renderer.updateUI(state)
        } else {
            nextTurn()
        }
    }

    /** Bayar denda lalu lempar dadu & bergerak (opsi penjara). */
    override suspend fun payJailFine() {
        if (state.phase != GamePhase.WAITING_FOR_ROLL) return

        // Test hooks: paksa giliran/posisi sebelum resolusi.
        testForcedTurn?.let { t ->
            repo.update { it.copy(currentPlayerIndex = t) }
            testForcedTurn = null
        }
        testForcedPosition?.let { (pid, pos) ->
            modifyPlayer(pid) { it.copy(position = pos) }
            testForcedPosition = null
        }

        val player = state.currentPlayer
        if (!player.inJail) return
        hookMessageActive = false
        val fine = rules.registry.jailFine
        if (player.money < fine) return

        modifyPlayer(player.id) { it.copy(inJail = false, jailTurns = 0, money = it.money - fine) }
        repo.update { it.copy(phase = GamePhase.ROLLING, message = "${player.name} bayar denda $$fine, keluar penjara.") }
        emit(GameEvent.JailExit, player, reason = "paid_fine")

        val roll = rollDice()
        renderer.animateDiceRoll(roll)
        delay(450)
        repo.update { it.copy(lastRoll = roll) }

        movePlayer(state.players[player.id], roll.total)
        delay(120)
        if (state.players[player.id].inJail) { nextTurn(); return }
        resolveLanding(state.players[player.id], roll)
        checkBankruptcy()
        if (state.isGameOver) { gameOver(); return }
        nextTurn()
    }

    private fun rollDice(): DiceRoll =
        forcedRolls.removeFirstOrNull() ?: DiceRoll(random.nextInt(1, 7), random.nextInt(1, 7))

    // ================================================================== penjara

    private suspend fun handleJailRoll(player: Player, roll: DiceRoll) {
        if (roll.isDouble) {
            // Kembar: bebas, gerak sesuai dadu. Turn BERAKHIR (tidak lempar lagi).
            modifyPlayer(player.id) { it.copy(inJail = false, jailTurns = 0) }
            repo.update { it.copy(message = "${player.name} keluar penjara dengan dadu kembar!") }
            emit(GameEvent.JailExit, player, reason = "doubles")
            movePlayer(state.players[player.id], roll.total)
            delay(120)
            resolveLanding(state.players[player.id], roll)
        } else {
            val jailTurns = player.jailTurns + 1
            val maxTurns = rules.registry.jailMaxTurns
            if (jailTurns >= maxTurns) {
                // Paksa bayar denda, lalu WAJIB bergerak.
                val fine = rules.registry.jailFine
                modifyPlayer(player.id) { it.copy(inJail = false, jailTurns = 0, money = it.money - fine) }
                repo.update { it.copy(message = "${player.name} bayar denda $$fine dan harus bergerak.") }
                emit(GameEvent.JailExit, player, reason = "forced")
                movePlayer(state.players[player.id], roll.total)
                delay(120)
                resolveLanding(state.players[player.id], roll)
            } else {
                modifyPlayer(player.id) { it.copy(jailTurns = jailTurns) }
                repo.update { it.copy(message = "${player.name} tetap di penjara ($jailTurns/$maxTurns).") }
            }
        }
        checkBankruptcy()
        if (state.isGameOver) { gameOver(); return }
        nextTurn()
    }

    private suspend fun sendToJail(player: Player) {
        val from = player.position
        modifyPlayer(player.id) {
            it.copy(inJail = true, jailTurns = 0, position = Player.JAIL_POSITION)
        }
        repo.update {
            it.copy(consecutiveDoubles = 0, message = "${player.name} langsung masuk penjara!")
        }
        renderer.animatePionMove(player.id, from, Player.JAIL_POSITION)
        emit(GameEvent.JailEnter, player, reason = "go_to_jail_card")
        renderer.updateUI(state)
    }

    // ============================================================== petak darat

    private suspend fun resolveLanding(player: Player, roll: DiceRoll, skipEvents: Boolean = false) {
        val tile = state.tiles.getOrNull(player.position) ?: return
        when (tile.type) {
            TileType.CORNER -> handleCorner(player, tile)
            TileType.EVENT -> if (skipEvents) repo.update { it.copy(message = "${player.name} berhenti di ${tile.name}.") }
                              else handleEvent(player, tile)
            else -> handleOwnable(player, tile, roll)
        }
        // Event mod: pemain mendarat di petak (setelah aturan bawaan).
        emit(GameEvent.Land, state.players[player.id], tile = tile, dice = roll)
        renderer.updateUI(state)
    }

    private suspend fun handleCorner(player: Player, tile: Tile) {
        when (tile.name) {
            "GO_TO_JAIL" -> sendToJail(player)
            "JAIL" -> repo.update { it.copy(message = "${player.name} hanya berkunjung ke penjara.") }
            "FREE_PARKING" -> repo.update { it.copy(message = "${player.name} di Free Parking, istirahat sebentar.") }
            "START" -> repo.update { it.copy(message = "${player.name} mendarat di START.") }
            else -> repo.update { it.copy(message = "${player.name} di ${tile.name}.") }
        }
    }

    private suspend fun handleEvent(player: Player, tile: Tile) {
        if (tile.taxAmount > 0 || tile.name.contains("Tax")) {
            val tax = if (tile.name.contains("Income")) rules.registry.incomeTax else rules.registry.luxuryTax
            payOrCollect(player, -tax, "Pajak ${tile.name}")
            return
        }
        val isChance = tile.name.contains("Chance")
        val card = drawCard(isChance)
        repo.update { it.copy(eventCard = card.text, message = card.text) }
        renderer.showEventCard(card.text)
        applyCard(player, card, lastRoll())
        // Event mod untuk kartu (setelah efek kartu diterapkan).
        val p = state.players[player.id]
        emit(
            GameEvent.CardDrawn, p,
            deck = card.deck.name, cardText = card.text
        )
        repo.update { it.copy(eventCard = null) }
    }

    private fun drawCard(isChance: Boolean): GameCard {
        // Test hook: kartu paksa.
        val forced = if (isChance) testForcedChance else testForcedChest
        if (forced != null) {
            if (isChance) testForcedChance = null else testForcedChest = null
            return forced
        }
        var deck = if (isChance) chanceDeck else chestDeck
        if (deck.isEmpty()) {
            deck = if (isChance) StandardDecks.CHANCE.shuffled(random) else StandardDecks.CHEST.shuffled(random)
        }
        val card = deck.first()
        if (isChance) chanceDeck = deck.drop(1) else chestDeck = deck.drop(1)
        return card
    }

    private suspend fun applyCard(player: Player, card: GameCard, roll: DiceRoll) {
        when {
            card.jailFree -> {
                modifyPlayer(player.id) { it.copy(jailCards = it.jailCards + 1) }
                repo.update { it.copy(message = "${player.name} menyimpan kartu Bebas Penjara.") }
                return
            }
            card.goToJail -> {
                sendToJail(player)
                return
            }
            card.perPlayerAmount != 0 -> transferPerPlayer(player, card.perPlayerAmount)
            card.perHouseAmount > 0 -> payPerHouse(player, card)
            card.amount != 0 -> payOrCollect(player, card.amount, card.text)
            else -> {
                val goTo = card.goTo
                val backSpaces = card.backSpaces
                when {
                    goTo != null -> {
                        moveToTile(player, goTo, forward = true)
                        delay(120)
                        resolveLanding(state.players[player.id], roll, skipEvents = true)
                    }
                    backSpaces > 0 -> {
                        moveToTile(player, player.position - backSpaces, forward = false)
                        delay(120)
                        resolveLanding(state.players[player.id], roll, skipEvents = true)
                    }
                }
            }
        }
        checkBankruptcy()
    }

    // ============================================================ beli & sewa

    private suspend fun handleOwnable(player: Player, tile: Tile, roll: DiceRoll) {
        val ownerId = state.tileOwners[tile.index]
        when {
            ownerId == null -> {
                if (player.money >= tile.price && tile.price > 0) {
                    val buy = renderer.showBuyDialog(tile.index)
                    if (buy) buyProperty(player, tile.index)
                    else repo.update { it.copy(message = "${player.name} melewati ${tile.name}.") }
                } else {
                    repo.update { it.copy(message = "${tile.name} tak terjangkau untuk ${player.name}.") }
                }
            }
            ownerId == player.id ->
                repo.update { it.copy(message = "${player.name} mendarat di propertinya sendiri.") }
            else -> {
                val owner = state.players[ownerId]
                if (tile.index in state.mortgaged) {
                    repo.update { it.copy(message = "${tile.name} dihipotik, tak ada sewa.") }
                } else {
                    val rent = computeRent(tile, owner, roll)
                    payRent(player, owner, rent, tile.name)
                }
            }
        }
    }

    override suspend fun buyCurrentProperty() {
        val player = state.currentPlayer
        val tile = state.tiles.getOrNull(player.position) ?: return
        if (state.tileOwners.containsKey(tile.index)) return
        if (player.money < tile.price) return
        buyProperty(player, tile.index)
        renderer.updateUI(state)
    }

    private suspend fun buyProperty(player: Player, tileIndex: Int) {
        val tile = state.tiles[tileIndex]
        modifyPlayer(player.id) {
            it.copy(money = it.money - tile.price, ownedProperties = it.ownedProperties + tileIndex)
        }
        repo.update {
            it.copy(
                tileOwners = it.tileOwners + (tileIndex to player.id),
                message = "${player.name} membeli ${tile.name} seharga $${tile.price}."
            )
        }
        emit(GameEvent.PropertyBought, state.players[player.id], tile = tile, price = tile.price)
    }

    /** Sewa menurut tipe petak & konteks (rumah, jumlah stasiun/utilitas, dadu). */
    private fun computeRent(tile: Tile, owner: Player, roll: DiceRoll): Int = when (tile.type) {
        TileType.RAILROAD -> {
            val count = state.tiles.count {
                it.type == TileType.RAILROAD &&
                    state.tileOwners[it.index] == owner.id &&
                    it.index !in state.mortgaged
            }
            // Rumus bawaan: 25/50/100/200 per jumlah dimiliki.
            tile.baseRent * (1 shl (count - 1).coerceAtLeast(0))
        }
        TileType.UTILITY -> {
            val count = state.tiles.count {
                it.type == TileType.UTILITY &&
                    state.tileOwners[it.index] == owner.id &&
                    it.index !in state.mortgaged
            }
            // Rumus bawaan: 4x/10x angka dadu.
            roll.total * if (count >= 2) 10 else 4
        }
        else -> tile.rentFor(
            state.houses[tile.index] ?: 0,
            ownsFullGroup(tile.colorGroup, owner.id) && rules.registry.monopolyDoublesRent
        )
    }

    private fun ownsFullGroup(group: String?, ownerId: Int): Boolean {
        if (group == null) return false
        val groupTiles = state.tiles.filter { it.colorGroup == group }
        return groupTiles.isNotEmpty() && groupTiles.all { state.tileOwners[it.index] == ownerId }
    }

    private suspend fun payRent(from: Player, to: Player, rent: Int, tileName: String) {
        val paid = minOf(from.money, rent)
        modifyPlayer(from.id) { it.copy(money = it.money - paid) }
        modifyPlayer(to.id) { it.copy(money = it.money + paid) }
        repo.update { it.copy(message = "${from.name} bayar sewa $$paid ke ${to.name} di $tileName.") }
        checkBankruptcy(creditorId = to.id)
    }

    // ========================================================= rumah & hipotik

    override suspend fun buildHouse(tileIndex: Int) {
        val s = state
        val player = s.currentPlayer
        if (s.phase != GamePhase.WAITING_FOR_ROLL) return
        val tile = s.tiles.getOrNull(tileIndex) ?: return
        if (tile.type != TileType.PROPERTY) return
        if (s.tileOwners[tileIndex] != player.id) return
        if (tileIndex in s.mortgaged) return
        if (!ownsFullGroup(tile.colorGroup, player.id)) return
        val current = s.houses[tileIndex] ?: 0
        val maxHouses = rules.registry.maxHouses
        if (current >= maxHouses) return
        // Bangun rata: tak boleh melebihi rumah minimum di grup.
        val groupMin = s.tiles.filter { it.colorGroup == tile.colorGroup }.minOf { s.houses[it.index] ?: 0 }
        if (current > groupMin) return
        if (player.money < tile.housePrice) return

        modifyPlayer(player.id) { it.copy(money = it.money - tile.housePrice) }
        val next = current + 1
        val kind = if (next >= maxHouses) "hotel" else "rumah"
        repo.update {
            it.copy(
                houses = it.houses + (tileIndex to next),
                message = "${player.name} membangun $kind di ${tile.name} — sewa jadi $${tile.rentFor(next, true)}."
            )
        }
        emit(GameEvent.HouseBuilt, state.players[player.id], tile = tile, houseCount = next)
        renderer.updateUI(state)
    }

    override suspend fun sellHouse(tileIndex: Int) {
        val s = state
        val player = s.currentPlayer
        if (s.phase != GamePhase.WAITING_FOR_ROLL) return
        val tile = s.tiles.getOrNull(tileIndex) ?: return
        if (tile.type != TileType.PROPERTY) return
        if (s.tileOwners[tileIndex] != player.id) return
        val current = s.houses[tileIndex] ?: 0
        if (current <= 0) return
        // Jual dari yang terbanyak (aturan rata).
        val groupMin = s.tiles.filter { it.colorGroup == tile.colorGroup }.minOf { s.houses[it.index] ?: 0 }
        if (current <= groupMin) return

        val refund = tile.housePrice / 2
        modifyPlayer(player.id) { it.copy(money = it.money + refund) }
        repo.update {
            it.copy(
                houses = it.houses + (tileIndex to current - 1),
                message = "${player.name} menjual rumah di ${tile.name} (+$$refund)."
            )
        }
        renderer.updateUI(state)
    }

    override suspend fun mortgageProperty(tileIndex: Int) {
        val s = state
        val player = s.currentPlayer
        if (s.phase != GamePhase.WAITING_FOR_ROLL) return
        val tile = s.tiles.getOrNull(tileIndex) ?: return
        if (tile.type == TileType.CORNER || tile.type == TileType.EVENT) return
        if (s.tileOwners[tileIndex] != player.id) return
        if (tileIndex in s.mortgaged) return
        // Tak boleh menghipotik properti yang ada rumahnya (grup penuh terblokir).
        if (tile.colorGroup != null &&
            s.tiles.any { it.colorGroup == tile.colorGroup && (s.houses[it.index] ?: 0) > 0 }
        ) return
        if (tile.mortgageValue <= 0) return

        modifyPlayer(player.id) { it.copy(money = it.money + tile.mortgageValue) }
        repo.update {
            it.copy(
                mortgaged = it.mortgaged + tileIndex,
                message = "${player.name} menghipotik ${tile.name} (+$${tile.mortgageValue})."
            )
        }
        renderer.updateUI(state)
    }

    override suspend fun unmortgageProperty(tileIndex: Int) {
        val s = state
        val player = s.currentPlayer
        if (s.phase != GamePhase.WAITING_FOR_ROLL) return
        val tile = s.tiles.getOrNull(tileIndex) ?: return
        if (tileIndex !in s.mortgaged) return
        if (s.tileOwners[tileIndex] != player.id) return
        val cost = (tile.mortgageValue * 1.1).toInt() // nilai + 10% biaya
        if (player.money < cost) return

        modifyPlayer(player.id) { it.copy(money = it.money - cost) }
        repo.update {
            it.copy(
                mortgaged = it.mortgaged - tileIndex,
                message = "${player.name} membebaskan hipotik ${tile.name} (-$$cost)."
            )
        }
        renderer.updateUI(state)
    }

    // =================================================================== kartu

    private suspend fun transferPerPlayer(player: Player, amount: Int) {
        val others = state.players.filter { it.id != player.id && !it.isBankrupt }
        if (others.isEmpty()) return
        val each = abs(amount)
        if (amount > 0) {
            var total = 0
            others.forEach { o ->
                val paid = minOf(o.money, each)
                modifyPlayer(o.id) { it.copy(money = it.money - paid) }
                total += paid
            }
            modifyPlayer(player.id) { it.copy(money = it.money + total) }
            repo.update { it.copy(message = "${player.name} menerima $$total dari ${others.size} pemain.") }
        } else {
            val perPlayer = (player.money.coerceAtLeast(0) / others.size).coerceAtMost(each)
            others.forEach { o -> modifyPlayer(o.id) { it.copy(money = it.money + perPlayer) } }
            modifyPlayer(player.id) { it.copy(money = it.money - perPlayer * others.size) }
            repo.update { it.copy(message = "${player.name} membayar $$perPlayer ke tiap pemain lain.") }
        }
    }

    private suspend fun payPerHouse(player: Player, card: GameCard) {
        val maxHouses = rules.registry.maxHouses
        var total = 0
        player.ownedProperties.forEach { idx ->
            val h = state.houses[idx] ?: 0
            total += if (h >= maxHouses) card.perHotelAmount else h * card.perHouseAmount
        }
        payOrCollect(player, -total, card.text)
    }

    private suspend fun payOrCollect(player: Player, amount: Int, reason: String) {
        modifyPlayer(player.id) { it.copy(money = it.money + amount) }
        val verb = if (amount >= 0) "terima" else "bayar"
        repo.update { it.copy(message = "${player.name} $verb $${abs(amount)} — $reason.") }
        checkBankruptcy()
    }

    // ================================================================= gerak

    private suspend fun movePlayer(player: Player, steps: Int) {
        moveToTile(player, player.position + steps, forward = true)
    }

    /**
     * Pindahkan ke petak absolut [index]. [forward] menentukan arah; hanya
     * gerak maju yang melewati START menghasilkan gaji $200.
     */
    private suspend fun moveToTile(player: Player, index: Int, forward: Boolean) {
        val from = player.position
        val to = ((index % boardSize) + boardSize) % boardSize
        val passedStart = forward && to < from
        val salary = rules.registry.goSalary
        modifyPlayer(player.id) {
            it.copy(position = to, money = if (passedStart) it.money + salary else it.money)
        }
        renderer.animatePionMove(player.id, from, to)
        if (passedStart) {
            val p = state.players[player.id]
            repo.update { it.copy(message = "${p.name} melewati START, terima $$salary!") }
            // Event mod (bisa menambah/mengubah efek).
            emit(GameEvent.PassStart, p, from = from, to = to)
        }
    }

    // ====================================================== bangkrut & giliran

    private suspend fun checkBankruptcy(creditorId: Int? = null) {
        val broke = state.players.firstOrNull { it.money < 0 && !it.isBankrupt } ?: return
        val forfeited = broke.ownedProperties
        val toCreditor = creditorId != null && creditorId != broke.id
        repo.update { s ->
            val newOwners = s.tileOwners.toMutableMap()
            forfeited.forEach { idx ->
                if (toCreditor) newOwners[idx] = creditorId else newOwners.remove(idx)
            }
            s.copy(
                players = s.players.map { p ->
                    when (p.id) {
                        broke.id -> p.copy(isBankrupt = true)
                        creditorId -> if (toCreditor) p.copy(ownedProperties = p.ownedProperties + forfeited) else p
                        else -> p
                    }
                },
                tileOwners = newOwners,
                houses = s.houses.filterKeys { it !in forfeited },
                mortgaged = s.mortgaged.filterNot { it in forfeited }.toSet(),
                message = "${broke.name} bangkrut dan keluar dari permainan!"
            )
        }
        // Event mod untuk kebangkrutan (mis. pesan berbeda).
        emit(
            GameEvent.Bankrupt, state.players[broke.id],
            creditor = creditorId?.let { state.players[it].name }
        )
        renderer.updateUI(state)
    }

    private suspend fun gameOver() {
        val winner = state.winner
        val worth = winner?.let { netWorth(it) }
        repo.update {
            it.copy(
                phase = GamePhase.GAME_OVER,
                message = winner?.let { p -> "Permainan selesai! Pemenang: ${p.name} (kekayaan $${netWorth(p)})." }
                    ?: "Permainan selesai."
            )
        }
        winner?.let { emit(GameEvent.GameOver, it, winner = it.name, netWorth = worth) }
        renderer.updateUI(state)
    }

    private fun nextTurn() {
        repo.update { s ->
            val next = nextActiveIndex(s)
            // Pesan kustom dari hook aturan bertahan sampai giliran berikutnya.
            val msg = if (hookMessageActive) s.message else "Giliran ${s.players[next].name}."
            s.copy(
                currentPlayerIndex = next,
                phase = GamePhase.WAITING_FOR_ROLL,
                consecutiveDoubles = 0,
                turnNumber = s.turnNumber + 1,
                message = msg
            )
        }
        hookMessageActive = false
        renderer.updateUI(state)
    }

    private fun nextActiveIndex(s: GameState): Int {
        var idx = (s.currentPlayerIndex + 1) % s.players.size
        var guard = 0
        while (s.players[idx].isBankrupt && guard < s.players.size) {
            idx = (idx + 1) % s.players.size
            guard++
        }
        return idx
    }

    /** Terapkan hasil override aturan kustom (Lua) ke pemain. */
    private suspend fun applyOutcome(playerId: Int, outcome: RuleOutcome) {
        if (outcome.moneyDelta != 0) {
            modifyPlayer(playerId) { it.copy(money = it.money + outcome.moneyDelta) }
        }
        if (outcome.goToJail) {
            sendToJail(state.players[playerId])
        }
        val msg = outcome.message
        if (msg != null) {
            repo.update { it.copy(message = msg) }
            hookMessageActive = true
        }
    }

    /**
     * Emit event ke bus mod (cetak biru dokumen 04 §2): dispatch semua
     * handler, terapkan hasil merge (uang/pesan/penjara) ke [player], lalu
     * terapkan aksi imperatif yang mengantri (M.giveMoney dst.).
     */
    private suspend fun emit(
        event: GameEvent,
        player: Player,
        tile: Tile? = null,
        dice: DiceRoll? = null,
        from: Int? = null,
        to: Int? = null,
        deck: String? = null,
        cardText: String? = null,
        reason: String? = null,
        creditor: String? = null,
        winner: String? = null,
        netWorth: Int? = null,
        price: Int? = null,
        houseCount: Int? = null
    ): RuleOutcome {
        val ctx = EventContext(
            player = snapshot(player),
            tile = tile?.let { tileSnapshot(it) },
            dice = dice?.let { DiceSnapshot(it.die1, it.die2, it.total, it.isDouble) },
            consecutiveDoubles = state.consecutiveDoubles,
            from = from, to = to, deck = deck, cardText = cardText,
            reason = reason, creditor = creditor, winner = winner, netWorth = netWorth,
            price = price, houseCount = houseCount, turnNumber = state.turnNumber
        )
        val outcome = rules.dispatch(event, ctx)
        applyOutcome(player.id, outcome)
        applyActions()
        return outcome
    }

    /** Terapkan aksi imperatif mod (M.*) yang mengantri setelah dispatch. */
    private suspend fun applyActions() {
        rules.drainActions().forEach { action ->
            when (action) {
                is ModAction.Money -> modifyPlayerByName(action.player) {
                    it.copy(money = it.money + action.delta)
                }
                is ModAction.Move -> {
                    val p = state.players.firstOrNull { it.name == action.player } ?: return@forEach
                    moveToTile(p, p.position + action.steps, forward = action.steps >= 0)
                }
                is ModAction.Jail -> modifyPlayerByName(action.player) {
                    if (action.enter) it.copy(inJail = true, jailTurns = 0, position = Player.JAIL_POSITION)
                    else it.copy(inJail = false, jailTurns = 0)
                }
                is ModAction.GrantCard -> modifyPlayerByName(action.player) {
                    it.copy(jailCards = it.jailCards + 1)
                }
                is ModAction.Message -> {
                    repo.update { it.copy(message = action.text) }
                    hookMessageActive = true
                }
                is ModAction.DrawCard -> {
                    val p = state.players.firstOrNull { it.name == action.player } ?: return@forEach
                    val card = drawCard(action.deck.equals("chest", ignoreCase = true))
                    repo.update { it.copy(eventCard = card.text, message = card.text) }
                    renderer.showEventCard(card.text)
                    applyCard(p, card, lastRoll())
                    emit(
                        GameEvent.CardDrawn, state.players[p.id],
                        deck = card.deck.name, cardText = card.text
                    )
                    repo.update { it.copy(eventCard = null) }
                }
            }
        }
        checkBankruptcy()
    }

    private suspend fun modifyPlayerByName(name: String, transform: (Player) -> Player) {
        repo.update { s ->
            s.copy(players = s.players.map { if (it.name == name) transform(it) else it })
        }
    }

    private fun snapshot(p: Player) =
        PlayerSnapshot(p.name, p.money, p.position, p.inJail, p.ownedProperties.toList())

    private fun tileSnapshot(t: Tile) = TileSnapshot(
        index = t.index, name = t.name, type = t.type.name, price = t.price,
        colorGroup = t.colorGroup,
        owner = state.tileOwners[t.index]?.let { state.players[it].name }
    )

    override fun reloadRules(): Boolean = rules.reload()

    override val ruleSourceName: String get() = rules.sourceName

    // ================================================================ util

    private fun netWorth(player: Player): Int = state.netWorth(player)

    private fun lastRoll(): DiceRoll = state.lastRoll ?: DiceRoll(1, 1)

    private suspend fun modifyPlayer(playerId: Int, transform: (Player) -> Player) {
        repo.update { s ->
            s.copy(players = s.players.map { if (it.id == playerId) transform(it) else it })
        }
    }
}
