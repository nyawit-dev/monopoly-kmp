package com.monopoly.data

import com.monopoly.core.EventContext
import com.monopoly.core.GameEvent
import com.monopoly.core.ModAction
import com.monopoly.core.RuleEngine
import com.monopoly.core.RuleOutcome
import com.monopoly.core.RuleRegistry
import com.monopoly.core.StateProvider

/**
 * Aturan baku Monopoli, tanpa mod. Dipakai di platform tanpa interpreter
 * Lua (JS) dan sebagai nilai dasar registry. Nilai identik dengan rules.lua
 * bawaan agar perilaku konsisten di semua platform.
 */
class StandardRuleEngine : RuleEngine {
    override val registry: RuleRegistry = RuleRegistry()
    override suspend fun dispatch(event: GameEvent, ctx: EventContext): RuleOutcome = RuleOutcome()
    override fun attachStateProvider(provider: StateProvider?) { /* tanpa mod: tak ada state yang dibaca */ }
    override fun drainActions(): List<ModAction> = emptyList()
    override val sourceName: String = "bawaan (aturan baku)"
    override fun reload(): Boolean = true
}
