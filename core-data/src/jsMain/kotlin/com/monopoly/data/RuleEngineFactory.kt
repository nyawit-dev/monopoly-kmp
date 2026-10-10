package com.monopoly.data

import com.monopoly.core.RuleEngine

/** JS: tanpa interpreter Lua — pakai aturan baku. */
actual fun createRuleEngine(): RuleEngine = StandardRuleEngine()
