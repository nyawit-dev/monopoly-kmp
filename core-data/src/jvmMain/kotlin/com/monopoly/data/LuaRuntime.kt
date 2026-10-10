package com.monopoly.data

import com.monopoly.core.GameEvent
import com.monopoly.core.ModAction
import com.monopoly.core.PlayerSnapshot
import com.monopoly.core.StateProvider
import com.monopoly.core.TileSnapshot
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Runtime Lua satu mod (cetak biru dokumen 07): Globals terpisah, sandbox
 * (pustaka berbahaya dibuang), dan gerbang `M` disuntikkan. Kegagalan
 * compile/eksekusi tak pernah keluar sebagai exception — semuanya lewat
 * callback log.
 */
class LuaRuntime(
    private val modId: String,
    private val capabilities: Set<String>,
    private val onLog: (level: String, msg: String) -> Unit,
    private val onSetRule: (key: String, value: Any) -> Unit,
    private val onGetRule: (key: String) -> Any?,
    private val onAction: (ModAction) -> Unit,
    private val stateProvider: () -> StateProvider?,
    private val tileProvider: () -> List<TileSnapshot>
) {
    /** Handler yang terdaftar mod ini lewat M.on. */
    data class Registered(val event: GameEvent, val priority: Int, val fn: LuaValue)

    val handlers: MutableList<Registered> = mutableListOf()

    private val globals: Globals = buildSandbox()

    /** Compile + jalankan kode mod (chunk). Error → false + log. */
    fun compile(code: String): Boolean {
        handlers.clear()
        return runCatching { globals.load(code).call() }
            .onFailure { e ->
                val msg = e.message?.lineSequence()?.firstOrNull() ?: e.toString()
                onLog("ERROR", "rules.lua: $msg")
            }
            .isSuccess
    }

    // ------------------------------------------------------------- sandbox

    private fun buildSandbox(): Globals {
        val g = JsePlatform.standardGlobals()
        runCatching {
            g.load(
                "os=nil io=nil debug=nil package=nil coroutine=nil " +
                    "dofile=nil loadfile=nil load=nil loadstring=nil java=nil luajava=nil"
            ).call()
        }
        g.set("print", luaFn { args -> onLog("INFO", args.arg(1).optjstring("")); LuaValue.NIL })
        g.set("M", buildM())
        return g
    }

    private fun luaFn(body: (Varargs) -> LuaValue): LuaValue = object : VarArgFunction() {
        override fun invoke(args: Varargs): LuaValue = body(args)
    }

    // ------------------------------------------------------------- gerbang M

    private fun buildM(): LuaTable = LuaTable.tableOf().also { m ->
        m.set("on", luaFn { args ->
            val key = args.arg(1).optjstring("")
            val event = GameEvent.fromKey(key)
            val f = args.arg(2)
            if (event == null || !f.isfunction()) {
                onLog("WARN", "M.on: event \"$key\" tidak dikenal atau argumen bukan fungsi")
                return@luaFn LuaValue.NIL
            }
            // Prioritas eksplisit bila diberikan; -1 = pakai prioritas mod
            // (diisi ModHost saat registrasi).
            val priority = if (args.narg() >= 3 && !args.arg(3).isnil()) {
                args.arg(3).optint(100)
            } else {
                -1
            }
            handlers.add(Registered(event, priority, f))
            LuaValue.NIL
        })

        m.set("setRule", luaFn { args ->
            val key = args.arg(1).optjstring("")
            val v = args.arg(2)
            val value: Any? = when {
                v.isboolean() -> v.toboolean()
                v.isint() -> v.toint()
                v.isnumber() -> v.todouble()
                else -> null
            }
            if (value == null) onLog("WARN", "setRule: nilai untuk \"$key\" bukan angka/boolean")
            else onSetRule(key, value)
            LuaValue.NIL
        })

        m.set("getRule", luaFn { args ->
            val key = args.arg(1).optjstring("")
            when (val v = onGetRule(key)) {
                is Int -> LuaValue.valueOf(v)
                is Boolean -> LuaValue.valueOf(v)
                else -> LuaValue.NIL
            }
        })

        m.set("log", luaFn { args -> onLog("INFO", args.arg(1).optjstring("")); LuaValue.NIL })
        m.set("message", guarded("message", "ui_message") { ModAction.Message(it.arg(1).optjstring("")) })

        m.set("giveMoney", guarded("giveMoney", "modify_money") {
            ModAction.Money(it.arg(1).optjstring(""), it.arg(2).optint(0))
        })
        m.set("takeMoney", guarded("takeMoney", "modify_money") {
            ModAction.Money(it.arg(1).optjstring(""), -it.arg(2).optint(0))
        })
        m.set("movePlayer", guarded("movePlayer", "move_players") {
            ModAction.Move(it.arg(1).optjstring(""), it.arg(2).optint(0))
        })
        m.set("sendToJail", guarded("sendToJail", "jail_control") {
            ModAction.Jail(it.arg(1).optjstring(""), true)
        })
        m.set("releaseFromJail", guarded("releaseFromJail", "jail_control") {
            ModAction.Jail(it.arg(1).optjstring(""), false)
        })
        m.set("grantJailCard", guarded("grantJailCard", "jail_control") {
            ModAction.GrantCard(it.arg(1).optjstring(""))
        })
        m.set("drawCard", guarded("drawCard", "draw_cards") {
            ModAction.DrawCard(it.arg(1).optjstring(""), it.arg(2).optjstring("chance"))
        })

        m.set("getState", luaFn { buildStateTable() })
        m.set("getTile", luaFn { args ->
            val idx = args.arg(1).optint(-1)
            tileProvider().firstOrNull { it.index == idx }?.let { tileTable(it) } ?: LuaValue.NIL
        })
    }

    /** Aksi yang butuh capability; tanpa izin → di-drop + warning. */
    private fun guarded(name: String, capability: String, build: (Varargs) -> ModAction): LuaValue =
        luaFn { args ->
            if (capability !in capabilities) {
                onLog("WARN", "aksi $name butuh capability \"$capability\"")
            } else {
                onAction(build(args))
            }
            LuaValue.NIL
        }

    private fun buildStateTable(): LuaValue {
        val sp = stateProvider()
        val t = LuaTable.tableOf()
        t.set("turn", LuaValue.valueOf(sp?.turnNumber() ?: 0))
        t.set("current_player", LuaValue.valueOf(sp?.currentPlayerName() ?: ""))
        val players = LuaTable.tableOf()
        sp?.players()?.forEachIndexed { i, p ->
            val pt = LuaTable.tableOf()
            pt.set("name", p.name)
            pt.set("money", p.money)
            pt.set("position", p.position)
            pt.set("in_jail", LuaValue.valueOf(p.inJail))
            val props = LuaTable.tableOf()
            p.properties.forEachIndexed { j, idx -> props.set(j + 1, LuaValue.valueOf(idx)) }
            pt.set("properties", props)
            players.set(i + 1, pt)
        }
        t.set("players", players)
        return t
    }

    private fun tileTable(t: TileSnapshot): LuaTable = LuaTable.tableOf().also { tb ->
        tb.set("index", t.index)
        tb.set("name", t.name)
        tb.set("type", t.type)
        tb.set("price", t.price)
        tb.set("color_group", t.colorGroup?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL)
        tb.set("owner", t.owner?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL)
    }
}
