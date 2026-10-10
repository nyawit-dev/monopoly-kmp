package com.monopoly.data

import com.monopoly.core.AssetLoaderPort
import com.monopoly.core.DiceRoll
import com.monopoly.core.GameCard
import com.monopoly.core.GamePhase
import com.monopoly.core.GameRendererPort
import com.monopoly.core.GameState
import com.monopoly.core.ModStatus
import com.monopoly.core.Player
import com.monopoly.core.RuleEngine
import com.monopoly.core.Tile
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// ================================================================= renderer

/** Renderer palsu: menangkap perintah tanpa mesin KorGE / tanpa delay nyata. */
internal class FakeRenderer(var buyAnswer: Boolean = true) : GameRendererPort {
    var buyAskedFor: Int? = null
    var cardShown: String? = null
    var lastState: GameState? = null

    override suspend fun animateDiceRoll(finalValue: DiceRoll) {}
    override suspend fun animatePionMove(playerId: Int, fromPosition: Int, toPosition: Int) {}
    override suspend fun showBuyDialog(tileIndex: Int): Boolean { buyAskedFor = tileIndex; return buyAnswer }
    override suspend fun showMessage(text: String) {}
    override suspend fun showEventCard(text: String) { cardShown = text }
    override fun updateUI(state: GameState) { lastState = state }
}

// ================================================================== loader

@Serializable
private data class BoardTiles(val tiles: List<Tile>)

/** Memuat papan standar 40 petak dari JSON (uji pakai papan asli). */
private object RealBoardLoader : AssetLoaderPort {
    override suspend fun loadBoardConfig(): List<Tile> {
        val raw = requireNotNull(javaClass.getResourceAsStream("/board_config.json")) {
            "board_config.json tidak ada di resources test"
        }.bufferedReader().use { it.readText() }
        return Json { ignoreUnknownKeys = true }
            .decodeFromString<BoardTiles>(raw).tiles.sortedBy { it.index }
    }
}

// ================================================================== helpers

/** Mesin aturan dasar saja (folder mods kosong) — deterministik untuk test. */
private fun baseRules(): RuleEngine =
    LuaRuleEngine(ModHost(java.nio.file.Files.createTempDirectory("monopoly-test-mods").toFile()))

private fun engine(
    board: List<Tile>,
    renderer: GameRendererPort = FakeRenderer(),
    rolls: List<DiceRoll> = emptyList(),
    names: List<String> = listOf("A", "B"),
    rules: RuleEngine = baseRules()
): GameEngineImpl = GameEngineImpl(GameRepository(board), renderer, Random(1), rules).apply {
    forcedRolls.addAll(rolls)
    startGame(names)
}

private val CHANCE_CARD_GO_TO_START = GameCard(GameCard.Deck.CHANCE, "Maju ke START (terima $200)", goTo = 0)
private val CHANCE_CARD_GO_TO_JAIL = GameCard(GameCard.Deck.CHANCE, "Langsung ke Penjara", goToJail = true)
private val CHEST_CARD_BANK_ERROR = GameCard(GameCard.Deck.CHEST, "Salah bank, untung $200", amount = 200)

// ====================================================================== test

/**
 * Test aturan engine memakai papan standar (JSON) + dadu & kartu terkontrol
 * lewat hook internal sehingga deterministik (bukan tebak seed).
 */
class GameEngineImplTest {

    private suspend fun tiles(): List<Tile> = RealBoardLoader.loadBoardConfig()

    // ---------------------------------------------------------- setup dasar

    @Test
    fun startGame_setsUpPlayersAndWaitingPhase() = runTest {
        val e = engine(tiles())
        val s = e.state
        assertEquals(2, s.players.size)
        assertEquals(GamePhase.WAITING_FOR_ROLL, s.phase)
        assertEquals(Player.STARTING_CASH, s.players[0].money)
        assertEquals(0, s.players[0].position)
        assertFalse(s.isGameOver)
    }

    // --------------------------------------------------------- melewati START

    @Test
    fun passingStart_grantsGoSalary() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 38 // 38+2=40 -> tepat di START
        e.rollDiceAndMove()
        val p = e.state.players[0]
        assertEquals(0, p.position, "harus membungkus tepat ke START")
        assertEquals(Player.STARTING_CASH + Player.GO_SALARY, p.money, "gaji $200 diterima")
    }

    // ------------------------------------------------------------ sewa dasar

    @Test
    fun landingOnOwnedProperty_paysRent() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 2)))
        e.rollDiceAndMove() // A mendarat petak 3 (Baltic, $60) & beli
        assertEquals(0, e.state.tileOwners[3])
        assertEquals(Player.STARTING_CASH - 60, e.state.players[0].money)
        assertEquals(1, e.state.currentPlayerIndex)

        e.forcedRolls.add(DiceRoll(1, 2)) // B: 0+3 -> petak 3 milik A
        e.rollDiceAndMove()
        val (a, b) = e.state.players
        assertEquals(Player.STARTING_CASH - 60 + 4, a.money, "A menerima sewa $4")
        assertEquals(Player.STARTING_CASH - 4, b.money, "B membayar sewa $4")
    }

    @Test
    fun landingOnOwnProperty_noRent() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 2)))
        e.rollDiceAndMove() // A beli petak 3
        val moneyAfterBuy = e.state.players[0].money

        // Giliran B dulu (cepat), lalu A kembali ke giliran & mendarat di petak 3.
        e.forcedRolls.add(DiceRoll(1, 2))
        e.rollDiceAndMove() // B: 0+3 -> petak 3, bayar sewa ke A
        e.testForcedTurn = 0
        e.testForcedPosition = 0 to 2 // dari petak 2, lempar 1 -> mendarat petak 3 (milik A)
        e.forcedRolls.add(DiceRoll(1, 0))
        e.rollDiceAndMove()
        val p = e.state.players[0]
        assertTrue(
            p.money >= moneyAfterBuy + 4,
            "mendarat di properti sendiri tidak memungut sewa (uang hanya naik dari sewa B)"
        )
    }

    // --------------------------------------------------------- sewa stasiun

    @Test
    fun railroadRent_doublesPerStationOwned() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(2, 3))) // A: 0+5 -> Reading RR
        e.rollDiceAndMove()
        assertEquals(0, e.state.tileOwners[5], "A membeli Reading Railroad")

        e.testGrantProperty(0, 15)
        e.testGrantProperty(0, 25)
        e.testGrantProperty(0, 35) // 4 stasiun total
        val aBefore = e.state.players[0].money
        val bBefore = e.state.players[1].money
        e.testForcedTurn = 1
        e.testForcedPosition = 1 to 0
        e.forcedRolls.add(DiceRoll(2, 3)) // B -> petak 5
        e.rollDiceAndMove()
        val (a, b) = e.state.players
        assertEquals(aBefore + 200, a.money, "sewa 4 stasiun = $200")
        assertEquals(bBefore - 200, b.money)
    }

    // ---------------------------------------------------------- sewa utilitas

    @Test
    fun utilityRent_fourOrTenTimesDice() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(6, 6))) // A: 0+12 -> Electric Company
        e.rollDiceAndMove()
        assertEquals(0, e.state.tileOwners[12], "A membeli Electric Company")

        // B dari petak 3, lempar 9 (non-kembar) -> petak 12: 4x9 = 36.
        e.testForcedTurn = 1
        e.testForcedPosition = 1 to 3
        val bBefore = e.state.players[1].money
        e.forcedRolls.add(DiceRoll(4, 5))
        e.rollDiceAndMove()
        assertEquals(bBefore - 36, e.state.players[1].money, "utilitas tunggal = 4x dadu ($36)")

        // Dua utilitas dimiliki -> 10x: 10x9 = 90.
        e.testGrantProperty(0, 28) // Water Works
        e.testForcedTurn = 1
        e.testForcedPosition = 1 to 3
        val b2 = e.state.players[1].money
        e.forcedRolls.add(DiceRoll(4, 5))
        e.rollDiceAndMove()
        assertEquals(b2 - 90, e.state.players[1].money, "dua utilitas = 10x dadu ($90)")
    }

    // ------------------------------------------------------------- pajak

    @Test
    fun incomeTax_deductsMoney() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(2, 2))) // A: 0+4 -> Income Tax
        e.rollDiceAndMove()
        assertEquals(Player.STARTING_CASH - 200, e.state.players[0].money, "Income Tax $200 dipotong")
    }

    @Test
    fun luxuryTax_deductsMoney() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 36 // 36+2 -> petak 38 = Luxury Tax
        e.rollDiceAndMove()
        assertEquals(Player.STARTING_CASH - 100, e.state.players[0].money, "Luxury Tax $100 dipotong")
    }

    // -------------------------------------------------------- rumah & hotel

    @Test
    fun buildHouse_requiresMonopoly_evenBuild_andRaisesRent() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(3, 3))) // A: 0+6 -> petak 6 (Oriental, LIGHTBLUE)
        e.rollDiceAndMove()
        assertEquals(0, e.state.tileOwners[6])

        e.testForcedTurn = 0
        e.buildHouse(6)
        assertEquals(0, e.state.houses[6] ?: 0, "tanpa grup penuh tak boleh bangun")

        e.testGrantProperty(0, 8)
        e.testGrantProperty(0, 9) // grup LIGHTBLUE lengkap
        val moneyBefore = e.state.players[0].money
        e.buildHouse(6)
        assertEquals(1, e.state.houses[6], "rumah terbangun")
        assertEquals(moneyBefore - 50, e.state.players[0].money, "harga rumah $50 dipotong")

        e.buildHouse(6)
        assertEquals(1, e.state.houses[6], "even build: tak boleh mendahului grup")

        e.buildHouse(8)
        e.buildHouse(9)
        e.buildHouse(6)
        assertEquals(2, e.state.houses[6], "setelah rata, boleh bangun lagi")

        // Sewa dengan 2 rumah di petak 6 (jadwal: 90). Roll B non-kembar
        // agar giliran berpindah rapi setelah resolve.
        e.testForcedTurn = 1
        e.testForcedPosition = 1 to 1 // dari petak 1, lempar 5 -> petak 6
        val bBefore = e.state.players[1].money
        e.forcedRolls.add(DiceRoll(3, 2))
        e.rollDiceAndMove()
        assertEquals(bBefore - 90, e.state.players[1].money, "sewa 2 rumah = $90")
    }

    @Test
    fun monopoly_withoutHouses_doublesBaseRent() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(3, 3)))
        e.rollDiceAndMove() // A beli petak 6
        e.testGrantProperty(0, 8)
        e.testGrantProperty(0, 9)
        e.testForcedTurn = 1
        e.testForcedPosition = 1 to 0
        val bBefore = e.state.players[1].money
        e.forcedRolls.add(DiceRoll(3, 3))
        e.rollDiceAndMove()
        assertEquals(bBefore - 12, e.state.players[1].money, "grup penuh tanpa rumah = x2 ($12)")
    }

    @Test
    fun sellHouse_refundsHalf_andKeepsEvenRule() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(3, 3)))
        e.rollDiceAndMove() // A beli petak 6
        e.testGrantProperty(0, 8)
        e.testGrantProperty(0, 9)
        e.testForcedTurn = 0
        e.buildHouse(6)
        e.buildHouse(8)
        e.buildHouse(9)
        e.buildHouse(6) // petak 6 & 8 punya 2, petak 9 punya 1
        val before = e.state.players[0].money
        e.sellHouse(6)
        assertEquals(1, e.state.houses[6])
        assertEquals(before + 25, e.state.players[0].money, "jual rumah = setengah harga ($25)")
        // Jual lagi saat sudah rata (semua 1) -> ditolak.
        e.sellHouse(6)
        assertEquals(1, e.state.houses[6], "even rule: tak boleh jual di bawah minimum grup")
    }

    // --------------------------------------------------------------- penjara

    @Test
    fun goToJail_sendsPlayerToJailPosition() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 28 // 28+2 -> petak 30 = GO_TO_JAIL
        e.rollDiceAndMove()
        val p = e.state.players[0]
        assertTrue(p.inJail, "GO_TO_JAIL harus memasukkan penjara")
        assertEquals(Player.JAIL_POSITION, p.position, "posisi pindah ke petak 10")
        assertEquals(Player.STARTING_CASH, p.money, "tidak ada uang yang berubah")
        assertEquals(1, e.state.currentPlayerIndex, "giliran berakhir")
    }

    @Test
    fun jail_doublesReleasesAndEndsTurn() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 28
        e.rollDiceAndMove() // A dipenjara

        e.testForcedTurn = 1
        e.forcedRolls.add(DiceRoll(1, 2))
        e.rollDiceAndMove() // B main

        e.testForcedTurn = 0
        e.testForcedPosition = 0 to Player.JAIL_POSITION
        e.forcedRolls.add(DiceRoll(2, 2))
        e.rollDiceAndMove() // A kembar -> bebas
        val p = e.state.players[0]
        assertFalse(p.inJail, "kembar membebaskan dari penjara")
        assertEquals(14, p.position, "bergerak 4 dari petak 10")
        assertEquals(1, e.state.currentPlayerIndex, "turn berakhir setelah bebas dengan kembar")
    }

    @Test
    fun jail_forcedFineAfterThreeTurns_thenMoves() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 28
        e.rollDiceAndMove() // A dipenjara

        repeat(2) {
            e.testForcedTurn = 0
            e.testForcedPosition = 0 to Player.JAIL_POSITION
            e.forcedRolls.add(DiceRoll(1, 2))
            e.rollDiceAndMove()
            assertTrue(e.state.players[0].inJail, "masih dipenjara")
        }
        e.testForcedTurn = 0
        e.testForcedPosition = 0 to Player.JAIL_POSITION
        e.forcedRolls.add(DiceRoll(4, 6)) // lemparan ke-3 (non-kembar): paksa bayar, total 10 -> petak 20 Free Parking
        e.rollDiceAndMove()
        val p = e.state.players[0]
        assertFalse(p.inJail, "setelah 3 giliran harus keluar")
        assertEquals(Player.STARTING_CASH - Player.JAIL_FINE, p.money, "denda $50 dipotong")
        assertEquals(20, p.position, "bergerak 10 dari petak 10 -> Free Parking (tanpa biaya)")
    }

    @Test
    fun payJailFine_freesAndMoves() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 28
        e.rollDiceAndMove() // A dipenjara

        e.testForcedTurn = 0
        e.testForcedPosition = 0 to Player.JAIL_POSITION
        e.forcedRolls.add(DiceRoll(4, 6)) // total 10 -> petak 20 Free Parking (tanpa beli)
        e.payJailFine()
        val p = e.state.players[0]
        assertFalse(p.inJail, "bayar denda membebaskan")
        assertEquals(Player.STARTING_CASH - Player.JAIL_FINE, p.money, "denda $50 dipotong")
        assertEquals(20, p.position, "bergerak 10 dari petak 10 -> Free Parking")
    }

    // ------------------------------------------------------- 3 dadu kembar

    @Test
    fun threeDoubles_sendsToJail() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1), DiceRoll(1, 1), DiceRoll(1, 1)))
        e.rollDiceAndMove() // kembar 1 -> lempar lagi
        assertEquals(0, e.state.currentPlayerIndex, "kembar: pemain sama")
        e.rollDiceAndMove() // kembar 2 -> lempar lagi
        e.rollDiceAndMove() // kembar 3 -> penjara
        val p = e.state.players[0]
        assertTrue(p.inJail, "3 kembar berturut-turut = penjara")
        assertEquals(Player.JAIL_POSITION, p.position)
        assertEquals(1, e.state.currentPlayerIndex, "giliran berakhir")
    }

    // ------------------------------------------------------------ hipotik

    @Test
    fun mortgage_collectsValue_andStopsRent() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(3, 3)))
        e.rollDiceAndMove() // A beli petak 6 ($100, mortgage $50)
        e.testForcedTurn = 0
        e.mortgageProperty(6)
        val p = e.state.players[0]
        assertEquals(Player.STARTING_CASH - 100 + 50, p.money, "terima nilai hipotik $50")
        assertTrue(6 in e.state.mortgaged)

        e.testForcedTurn = 1
        e.testForcedPosition = 1 to 1 // dari petak 1, lempar 5 (non-kembar) -> petak 6
        val bBefore = e.state.players[1].money
        e.forcedRolls.add(DiceRoll(3, 2))
        e.rollDiceAndMove()
        assertEquals(bBefore, e.state.players[1].money, "properti dihipotik tidak menghasilkan sewa")

        // Bebaskan hipotik: bayar $50 + 10% = $55 (giliran sudah kembali ke A).
        val aBefore = e.state.players[0].money
        e.unmortgageProperty(6)
        assertEquals(aBefore - 55, e.state.players[0].money, "bebaskan hipotik = nilai + 10% ($55)")
        assertTrue(6 !in e.state.mortgaged)
    }

    @Test
    fun mortgage_blockedWhenHousesInGroup() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(3, 3)))
        e.rollDiceAndMove() // A beli petak 6
        e.testGrantProperty(0, 8)
        e.testGrantProperty(0, 9)
        e.testForcedTurn = 0
        e.buildHouse(6)
        val before = e.state.players[0].money
        e.mortgageProperty(6)
        assertEquals(before, e.state.players[0].money, "ada rumah di grup -> tak boleh hipotik")
        assertTrue(6 !in e.state.mortgaged)
    }

    // -------------------------------------------------------------- kartu

    @Test
    fun chanceCard_advanceToGo() = runTest {
        val r = FakeRenderer()
        val e = engine(tiles(), renderer = r, rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 20 // 20+2 -> petak 22 = Chance
        e.testForcedChance = CHANCE_CARD_GO_TO_START
        e.rollDiceAndMove()
        val p = e.state.players[0]
        assertEquals(0, p.position)
        // Kartu "Maju ke START" resmi: "Collect $200" — memang menerima gaji.
        assertEquals(Player.STARTING_CASH + Player.GO_SALARY, p.money, "kartu maju ke START menerima $200")
        assertTrue(r.cardShown?.contains("START") == true, "kartu ditampilkan ke renderer")
        assertNull(e.state.eventCard, "kartu ditutup setelah resolve")
    }

    @Test
    fun chanceCard_goToJail_sendsToJail() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 20
        e.testForcedChance = CHANCE_CARD_GO_TO_JAIL
        e.rollDiceAndMove()
        val p = e.state.players[0]
        assertTrue(p.inJail, "kartu penjara mempenjarakan")
        assertEquals(Player.JAIL_POSITION, p.position)
    }

    @Test
    fun chestCard_bankError_collectsMoney() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 1)))
        e.testForcedPosition = 0 to 0 // 0+2 -> petak 2 = Community Chest
        e.testForcedChest = CHEST_CARD_BANK_ERROR
        e.rollDiceAndMove()
        assertEquals(Player.STARTING_CASH + 200, e.state.players[0].money)
    }

    // ---------------------------------------------------------- state imutable

    @Test
    fun state_playersAreImmutable_snapshots() = runTest {
        val e = engine(tiles(), rolls = listOf(DiceRoll(1, 2)))
        val before = e.state.players[0]
        e.rollDiceAndMove()
        val after = e.state.players[0]
        assertEquals(0, before.position, "snapshot lama tak berubah (immutable)")
        assertEquals(3, after.position, "snapshot baru memuat posisi terbaru")
        assertTrue(before !== after, "Player adalah objek baru, bukan mutasi")
    }

    // ------------------------------------------------- aturan & mod (Lua)

    /** Buat mod sementara di folder temp; kembalikan root folder mods. */
    private fun tempMods(
        lua: String,
        priority: Int = 200,
        capabilities: List<String> = listOf("read_state", "modify_money", "ui_message"),
        id: String = "uji-lua"
    ): File {
        val root = java.nio.file.Files.createTempDirectory("monopoly-mods").toFile()
        val dir = File(root, id)
        File(dir, "scripts").mkdirs()
        File(dir, "mod.json").writeText(
            """
            {
              "id": "$id",
              "name": "Uji Lua",
              "version": "1.0.0",
              "api_version": 1,
              "priority": $priority,
              "roles": ["rules"],
              "capabilities": [${capabilities.joinToString(",") { "\"$it\"" }}]
            }
            """.trimIndent()
        )
        File(dir, "scripts/rules.lua").writeText(lua)
        return root
    }

    @Test
    fun customLuaRules_overrideStartingCashAndSalary() = runTest {
        val lua = LuaRuleEngine(ModHost(tempMods(
            "M.setRule(\"starting_cash\", 2000)\nM.setRule(\"go_salary\", 500)"
        )))
        assertEquals(2000, lua.registry.startingCash, "modal awal dari Lua")
        assertEquals(500, lua.registry.goSalary, "gaji START dari Lua")

        val e = GameEngineImpl(GameRepository(tiles()), FakeRenderer(), Random(1), lua)
        e.startGame(listOf("A", "B"))
        assertEquals(2000, e.state.players[0].money, "engine pakai modal dari Lua")

        e.testForcedPosition = 0 to 38
        e.forcedRolls.add(DiceRoll(3, 3))
        e.rollDiceAndMove()
        // 38+6 -> membungkus ke petak 4 (Income Tax): gaji +500, pajak -200.
        assertEquals(2000 + 500 - 200, e.state.players[0].money, "gaji START kustom $500 diterapkan")
    }

    @Test
    fun customLuaRules_onLandHook_appliesMoneyDelta() = runTest {
        val lua = LuaRuleEngine(ModHost(tempMods(
            """
            M.on("land", function(ctx)
                if ctx.tile and ctx.tile.name == "Income Tax" then
                    return { money_delta = 777, message = "Uang roh di pajak!" }
                end
                return nil
            end)
            """.trimIndent()
        )))
        val e = GameEngineImpl(GameRepository(tiles()), FakeRenderer(), Random(1), lua)
        e.startGame(listOf("A", "B"))
        e.forcedRolls.add(DiceRoll(2, 2)) // 0+4 -> Income Tax (pajak -200, hook +777)
        e.rollDiceAndMove()
        assertEquals(1500 - 200 + 777, e.state.players[0].money, "hook land menambah uang")
        assertTrue(e.state.message.contains("Uang roh"), "pesan kustom dari Lua dipakai")
    }

    @Test
    fun customLuaRules_disableThreeDoublesJail() = runTest {
        val lua = LuaRuleEngine(ModHost(tempMods(
            "M.setRule(\"three_doubles_to_jail\", false)"
        )))
        val e = GameEngineImpl(GameRepository(tiles()), FakeRenderer(), Random(1), lua)
        e.startGame(listOf("A", "B"))
        repeat(3) { e.forcedRolls.add(DiceRoll(1, 1)) }
        e.rollDiceAndMove()
        e.rollDiceAndMove()
        e.rollDiceAndMove()
        assertFalse(e.state.players[0].inJail, "aturan 3-kembar dimatikan lewat Lua")
    }

    @Test
    fun invalidLuaRules_fallsBackToStandard() = runTest {
        val lua = LuaRuleEngine(ModHost(tempMods("ini bukan lua {{{ ### !!!")))
        assertEquals(200, lua.registry.goSalary, "Lua rusak -> jatuh ke aturan baku")
        assertEquals(1500, lua.registry.startingCash)
        assertEquals(ModStatus.ACTIVE_DEGRADED, lua.host.mods.value.single().status,
            "mod rusak ditandai degradasi, bukan mati total")
        assertEquals(true, lua.reload(), "reload tetap berhasil walau Lua rusak")
    }
}
