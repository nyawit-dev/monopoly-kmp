package com.monopoly.data

import com.monopoly.core.DiceRoll
import com.monopoly.core.ModStatus
import com.monopoly.core.Tile
import com.monopoly.core.TileType
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Uji ModHost (cetak biru dokumen 02/03/04/07): validasi manifest,
 * dependensi, konflik, sandbox, bus event, merge, isolasi galat.
 */
class ModHostTest {

    private fun tempRoot(): File = Files.createTempDirectory("monopoly-mods").toFile()

    private fun writeMod(
        root: File,
        id: String,
        lua: String? = null,
        priority: Int = 100,
        capabilities: List<String> = listOf("read_state", "modify_money", "ui_message"),
        depends: List<String> = emptyList(),
        conflicts: List<String> = emptyList(),
        version: String = "1.0.0",
        apiVersion: Int = 1,
        enabled: Boolean = true,
        rawManifest: String? = null
    ) {
        val dir = File(root, id)
        File(dir, "scripts").mkdirs()
        val manifest = rawManifest ?: """
            {
              "id": "$id",
              "name": "Mod $id",
              "version": "$version",
              "api_version": $apiVersion,
              "priority": $priority,
              "enabled": $enabled,
              "roles": ["rules"],
              "capabilities": [${capabilities.joinToString(",") { "\"$it\"" }}],
              "depends": [${depends.joinToString(",") { "\"$it\"" }}],
              "conflicts": [${conflicts.joinToString(",") { "\"$it\"" }}]
            }
        """.trimIndent()
        File(dir, "mod.json").writeText(manifest)
        if (lua != null) File(dir, "scripts/rules.lua").writeText(lua)
    }

    private fun syntheticBoard(size: Int = 8): List<Tile> = List(size) { i ->
        Tile(
            index = i, name = "P$i", type = TileType.PROPERTY, colorGroup = null,
            price = 100, baseRent = 10, rentSchedule = listOf(10, 20, 30, 40, 50, 60),
            housePrice = 50, mortgageValue = 50, taxAmount = 0
        )
    }

    private fun modOf(host: ModHost, id: String) =
        assertNotNull(host.mods.value.firstOrNull { it.id == id }, "mod $id harus terdaftar").let { it }

    // ------------------------------------------------------- validasi manifest

    @Test
    fun invalidJson_isError() {
        val root = tempRoot()
        writeMod(root, "rusak", rawManifest = "bukan json {{{")
        val host = ModHost(root)
        val m = modOf(host, "rusak")
        assertEquals(ModStatus.ERROR, m.status)
        assertTrue(m.error!!.contains("mod.json"), "pesan galat menyebut mod.json")
    }

    @Test
    fun missingRequiredField_isError() {
        val root = tempRoot()
        writeMod(
            root, "minus",
            rawManifest = """{"name": "Tanpa id", "version": "1.0.0", "api_version": 1}"""
        )
        val host = ModHost(root)
        assertEquals(ModStatus.ERROR, modOf(host, "minus").status)
    }

    @Test
    fun idMismatchFolder_isError() {
        val root = tempRoot()
        writeMod(
            root, "folder-a",
            rawManifest = """{"id": "folder-b", "name": "X", "version": "1.0.0", "api_version": 1}"""
        )
        val host = ModHost(root)
        val m = modOf(host, "folder-b")
        assertEquals(ModStatus.ERROR, m.status)
        assertTrue(m.error!!.contains("tidak cocok"), "pesan galat menyebut ketidakcocokan")
    }

    @Test
    fun wrongApiVersion_isError() {
        val root = tempRoot()
        writeMod(root, "api", apiVersion = 2, lua = "M.log(\"jalan\")")
        val host = ModHost(root)
        val m = modOf(host, "api")
        assertEquals(ModStatus.ERROR, m.status)
        assertTrue(m.error!!.contains("api_version"))
    }

    @Test
    fun unknownCapability_droppedWithWarning() {
        val root = tempRoot()
        writeMod(
            root, "cap",
            lua = "M.log(\"mod jalan\")",
            capabilities = listOf("read_state", "baca_pikiran")
        )
        val host = ModHost(root)
        assertEquals(ModStatus.ACTIVE, modOf(host, "cap").status)
        assertTrue(
            host.log.value.any { it.level == "WARN" && it.message.contains("baca_pikiran") },
            "capability tak dikenal dibuang dengan peringatan"
        )
    }

    @Test
    fun unknownRuleKey_warnsAndKeepsBaseValue() {
        val root = tempRoot()
        writeMod(root, "aneh", lua = "M.setRule(\"kunci_hantu\", 5)")
        val host = ModHost(root)
        assertTrue(
            host.log.value.any { it.level == "WARN" && it.message.contains("kunci_hantu") },
            "kunci aturan tak dikenal diperingatkan"
        )
        assertEquals(200, host.registry.goSalary, "nilai basis tetap")
    }

    // ------------------------------------------------------- dependensi

    @Test
    fun missingDependency_isError() {
        val root = tempRoot()
        writeMod(root, "butuh", depends = listOf("tidak-ada@^1.0"))
        val host = ModHost(root)
        val m = modOf(host, "butuh")
        assertEquals(ModStatus.ERROR, m.status)
        assertTrue(m.error!!.contains("tidak terpasang"))
    }

    @Test
    fun dependencyVersionMismatch_isError() {
        val root = tempRoot()
        writeMod(root, "dep", version = "1.0.0")
        writeMod(root, "butuh", depends = listOf("dep@^2.0"))
        val host = ModHost(root)
        val m = modOf(host, "butuh")
        assertEquals(ModStatus.ERROR, m.status)
        assertTrue(m.error!!.contains("butuh"))
    }

    @Test
    fun satisfiedDependency_isActive() {
        val root = tempRoot()
        writeMod(root, "dep", version = "1.2.0")
        writeMod(root, "butuh", depends = listOf("dep@^1.0"))
        val host = ModHost(root)
        assertEquals(ModStatus.ACTIVE, modOf(host, "dep").status)
        assertEquals(ModStatus.ACTIVE, modOf(host, "butuh").status)
    }

    @Test
    fun conflict_marksBothError() {
        val root = tempRoot()
        writeMod(root, "a", conflicts = listOf("b"))
        writeMod(root, "b")
        val host = ModHost(root)
        assertEquals(ModStatus.ERROR, modOf(host, "a").status)
        assertEquals(ModStatus.ERROR, modOf(host, "b").status)
    }

    @Test
    fun dependencyCycle_marksBothError() {
        val root = tempRoot()
        writeMod(root, "a", depends = listOf("b@^1.0"))
        writeMod(root, "b", depends = listOf("a@^1.0"))
        val host = ModHost(root)
        assertEquals(ModStatus.ERROR, modOf(host, "a").status)
        assertEquals(ModStatus.ERROR, modOf(host, "b").status)
        assertTrue(host.log.value.any { it.message.contains("siklik") })
    }

    // ------------------------------------------------------- sandbox

    @Test
    fun sandbox_removesDangerousGlobals() {
        val root = tempRoot()
        writeMod(
            root, "sbox",
            lua = """
            M.log("os=" .. tostring(os))
            M.log("io=" .. tostring(io))
            M.log("load=" .. tostring(load))
            M.log("debug=" .. tostring(debug))
            M.log("dofile=" .. tostring(dofile))
            """
        )
        val host = ModHost(root)
        val msgs = host.log.value.filter { it.level == "INFO" }.map { it.message }
        listOf("os", "io", "load", "debug", "dofile").forEach { name ->
            assertTrue(msgs.any { it == "$name=nil" }, "globals $name harus nil di sandbox")
        }
    }

    @Test
    fun print_redirectedToLog() {
        val root = tempRoot()
        writeMod(root, "cetak", lua = "print(\"halo dari mod\")")
        val host = ModHost(root)
        assertTrue(
            host.log.value.any { it.level == "INFO" && it.message.contains("halo dari mod") },
            "print dialihkan ke log mod"
        )
    }

    // ------------------------------------------------------- setRule

    @Test
    fun setRule_overrideViaMod() {
        val root = tempRoot()
        writeMod(root, "aturan", lua = "M.setRule(\"go_salary\", 500)")
        val host = ModHost(root)
        assertEquals(500, host.registry.goSalary, "mod menimpa aturan")
        assertEquals(1500, host.registry.startingCash, "basis tetap untuk yang tak diubah")
    }

    // ------------------------------------------------------- state.json

    @Test
    fun disabledMod_notActive() {
        val root = tempRoot()
        writeMod(root, "off", enabled = false)
        val host = ModHost(root)
        assertEquals(ModStatus.DISABLED, modOf(host, "off").status)
    }

    @Test
    fun setEnabled_persistsAcrossReload() {
        val root = tempRoot()
        writeMod(root, "x")
        val host = ModHost(root)
        assertEquals(ModStatus.ACTIVE, modOf(host, "x").status)
        host.setEnabled("x", false)
        assertEquals(ModStatus.DISABLED, modOf(host, "x").status)
        host.reload()
        assertEquals(ModStatus.DISABLED, modOf(host, "x").status, "state.json bertahan")
        assertTrue(File(root, "state.json").isFile, "state.json ditulis di folder mods")
    }

    // ------------------------------------------------------- bus event

    @Test
    fun eventBus_priorityOrder() = runTest {
        val root = tempRoot()
        writeMod(root, "rendah", lua = "M.on(\"land\", function(ctx) M.log(\"rendah jalan\") end)")
        writeMod(root, "tinggi", priority = 300, lua = "M.on(\"land\", function(ctx) M.log(\"tinggi jalan\") end)")
        val host = ModHost(root)
        val e = GameEngineImpl(GameRepository(syntheticBoard()), FakeRenderer(), Random(1), LuaRuleEngine(host))
        e.startGame(listOf("A", "B"))
        e.forcedRolls.add(DiceRoll(1, 2)) // 0+3 -> P3
        e.rollDiceAndMove()
        val jalan = host.log.value.filter { it.message.endsWith("jalan") }.map { it.message }
        assertEquals(listOf("tinggi jalan", "rendah jalan"), jalan, "prioritas tinggi jalan lebih dulu")
    }

    @Test
    fun eventBus_stopCancelsLowerPriority() = runTest {
        val root = tempRoot()
        writeMod(root, "rendah", lua = "M.on(\"land\", function(ctx) M.log(\"rendah jalan\") end)")
        writeMod(
            root, "tinggi", priority = 300,
            lua = "M.on(\"land\", function(ctx) return { stop = true } end)"
        )
        val host = ModHost(root)
        val e = GameEngineImpl(GameRepository(syntheticBoard()), FakeRenderer(), Random(1), LuaRuleEngine(host))
        e.startGame(listOf("A", "B"))
        e.forcedRolls.add(DiceRoll(1, 2))
        e.rollDiceAndMove()
        assertFalse(
            host.log.value.any { it.message == "rendah jalan" },
            "stop membatalkan handler prioritas rendah"
        )
    }

    @Test
    fun merge_moneyAdditiveAndMessageLastWins() = runTest {
        val root = tempRoot()
        writeMod(
            root, "rendah",
            lua = "M.on(\"land\", function(ctx) return { money_delta = 50, message = \"dari rendah\" } end)"
        )
        writeMod(
            root, "tinggi", priority = 300,
            lua = "M.on(\"land\", function(ctx) return { money_delta = 100, message = \"dari tinggi\" } end)"
        )
        val e = GameEngineImpl(
            GameRepository(syntheticBoard()), FakeRenderer(), Random(1),
            LuaRuleEngine(ModHost(root))
        )
        e.startGame(listOf("A", "B"))
        e.forcedRolls.add(DiceRoll(1, 2)) // beli P3 (-100), lalu +50 +100
        e.rollDiceAndMove()
        assertEquals(1500 - 100 + 150, e.state.players[0].money, "money_delta aditif")
        assertEquals("dari rendah", e.state.message, "message last-wins (handler terakhir)")
    }

    @Test
    fun merge_goToJailStickyOr() = runTest {
        val root = tempRoot()
        writeMod(root, "penjara", lua = "M.on(\"land\", function(ctx) return { go_to_jail = true } end)")
        val e = GameEngineImpl(
            GameRepository(syntheticBoard()), FakeRenderer(), Random(1),
            LuaRuleEngine(ModHost(root))
        )
        e.startGame(listOf("A", "B"))
        e.forcedRolls.add(DiceRoll(1, 2))
        e.rollDiceAndMove()
        assertTrue(e.state.players[0].inJail, "satu handler cukup mengirim penjara (sticky OR)")
        assertEquals(10, e.state.players[0].position)
    }

    @Test
    fun handlerError_isolatedGameContinues() = runTest {
        val root = tempRoot()
        writeMod(root, "meledak", lua = "M.on(\"land\", function(ctx) error(\"ledakan\") end)")
        writeMod(root, "aman", priority = 50, lua = "M.on(\"land\", function(ctx) return { money_delta = 100 } end)")
        val host = ModHost(root)
        val e = GameEngineImpl(GameRepository(syntheticBoard()), FakeRenderer(), Random(1), LuaRuleEngine(host))
        e.startGame(listOf("A", "B"))
        e.forcedRolls.add(DiceRoll(1, 2))
        e.rollDiceAndMove()
        assertEquals(1500 - 100 + 100, e.state.players[0].money, "handler lain tetap jalan")
        assertTrue(
            host.log.value.any { it.level == "ERROR" && it.message.contains("ledakan") },
            "galat handler masuk log, game tak berhenti"
        )
    }

    // ------------------------------------------------------- reload

    @Test
    fun reload_picksUpNewMod() {
        val root = tempRoot()
        writeMod(root, "a", lua = "M.setRule(\"go_salary\", 321)")
        val host = ModHost(root)
        assertEquals(321, host.registry.goSalary)
        writeMod(root, "b", lua = "M.setRule(\"go_salary\", 654)")
        host.reload()
        assertEquals(654, host.registry.goSalary, "mod baru ter-load setelah reload")
    }
}
