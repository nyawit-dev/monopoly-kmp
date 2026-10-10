package com.monopoly.data

import com.monopoly.core.EventContext
import com.monopoly.core.GameEvent
import com.monopoly.core.PlayerSnapshot
import com.monopoly.core.RuleOutcome
import com.monopoly.core.TileSnapshot
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue

/** Bangun tabel Lua dari snapshot pemain (dipakai ctx & getState). */
internal fun playerTable(p: PlayerSnapshot): LuaTable = LuaTable.tableOf().also { t ->
    t.set("name", p.name)
    t.set("money", p.money)
    t.set("position", p.position)
    t.set("in_jail", LuaValue.valueOf(p.inJail))
    val props = LuaTable.tableOf()
    p.properties.forEachIndexed { j, idx -> props.set(j + 1, LuaValue.valueOf(idx)) }
    t.set("properties", props)
}

/** Bangun tabel Lua dari snapshot petak. */
internal fun tileTable(t: TileSnapshot): LuaTable = LuaTable.tableOf().also { tb ->
    tb.set("index", t.index)
    tb.set("name", t.name)
    tb.set("type", t.type)
    tb.set("price", t.price)
    tb.set("color_group", t.colorGroup?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL)
    tb.set("owner", t.owner?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL)
}

/**
 * Bangun tabel ctx yang diteruskan ke setiap handler event (cetak biru
 * dokumen 04 §2). Field tak relevan untuk event tidak di-set (nil di Lua).
 */
internal fun EventContext.toLuaTable(): LuaTable = LuaTable.tableOf().also { t ->
    t.set("player", playerTable(player))
    tile?.let { t.set("tile", tileTable(it)) }
    dice?.let { d ->
        val dt = LuaTable.tableOf()
        dt.set("d1", d.d1)
        dt.set("d2", d.d2)
        dt.set("total", d.total)
        dt.set("is_double", LuaValue.valueOf(d.isDouble))
        t.set("dice", dt)
    }
    if (consecutiveDoubles != 0) t.set("consecutive_doubles", consecutiveDoubles)
    from?.let { t.set("from", it) }
    to?.let { t.set("to", it) }
    deck?.let { t.set("deck", it) }
    cardText?.let { t.set("card_text", it) }
    reason?.let { t.set("reason", it) }
    creditor?.let { t.set("creditor", it) }
    winner?.let { t.set("winner", it) }
    netWorth?.let { t.set("net_worth", it) }
    price?.let { t.set("price", it) }
    houseCount?.let { t.set("house_count", it) }
    turnNumber?.let { t.set("turn_number", it) }
}

/**
 * Bus event mod (cetak biru dokumen 04 §4). Handler dari semua mod
 * didaftar di sini; dispatch berurutan priority desc (stabil by modId),
 * dengan semantik merge: money_delta aditif, message last-wins,
 * go_to_jail sticky-OR, stop menghentikan sisa rantai. Handler yang
 * melempar error di-skip + dilaporkan lewat [onWarn] — tak pernah fatal.
 */
class ModEventBus {
    private data class H(val modId: String, val event: GameEvent, val priority: Int, val fn: LuaValue)

    private val handlers = mutableListOf<H>()

    fun register(modId: String, event: GameEvent, priority: Int, fn: LuaValue) {
        handlers.add(H(modId, event, priority, fn))
    }

    fun clear() = handlers.clear()

    fun handlerCount(): Int = handlers.size

    fun dispatch(event: GameEvent, ctx: EventContext, onWarn: (String, String) -> Unit): RuleOutcome {
        val ctxTable = ctx.toLuaTable()
        val ordered = handlers
            .filter { it.event == event }
            .sortedWith(compareByDescending<H> { it.priority }.thenBy { it.modId })
        var money = 0
        var message: String? = null
        var jail = false
        for (h in ordered) {
            val ret = runCatching { h.fn.call(ctxTable) }.getOrElse { e ->
                val msg = e.message?.lineSequence()?.firstOrNull() ?: e.toString()
                onWarn(h.modId, "error handler ${event.key}: $msg")
                null
            } ?: continue
            if (ret.istable()) {
                money += ret.get("money_delta").optint(0)
                ret.get("message").optjstring(null)?.let { message = it }
                if (ret.get("go_to_jail").optboolean(false)) jail = true
                if (ret.get("stop").optboolean(false)) break
            }
        }
        return RuleOutcome(money, message, jail)
    }
}
