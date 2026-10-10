package com.monopoly.data

import com.monopoly.core.RuleEngine

/**
 * Buat mesin aturan. Di JVM memakai Lua (user bisa edit rules.lua tanpa
 * menyentuh kode); di platform lain (JS) memakai aturan baku.
 */
expect fun createRuleEngine(): RuleEngine
