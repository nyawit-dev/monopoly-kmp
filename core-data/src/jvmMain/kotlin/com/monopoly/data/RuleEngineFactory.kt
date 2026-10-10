package com.monopoly.data

import com.monopoly.core.RuleEngine
import java.io.File

/**
 * JVM: mesin aturan lewat ModHost — mods/ folder (user-editable) + basis
 * rules.lua bawaan. Mod rusak tak pernah menghentikan game (cetak biru
 * dokumen 02/07).
 */
actual fun createRuleEngine(): RuleEngine = LuaRuleEngine(ModHost(File("mods")))
