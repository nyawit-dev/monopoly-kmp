package com.monopoly.core

/**
 * Nilai-nilai aturan game yang bisa ditulis mod lewat `M.setRule`.
 * Default-nya aturan Monopoli baku; basis (rules.lua bawaan) dan mod user
 * menimpa lewat [set]. Engine selalu membaca dari sini, tak pernah dari
 * konstanta hardcode.
 */
data class RuleRegistry(
    val startingCash: Int = 1500,
    val goSalary: Int = 200,
    val jailFine: Int = 50,
    val jailMaxTurns: Int = 3,
    val maxHouses: Int = 5,
    val threeDoublesToJail: Boolean = true,
    val monopolyDoublesRent: Boolean = true,
    val incomeTax: Int = 200,
    val luxuryTax: Int = 100
) {
    /** Tulis nilai aturan. Kunci tak dikenal → registry tidak berubah. */
    fun set(key: String, value: Any): RuleRegistry = when (key) {
        "starting_cash" -> copy(startingCash = value.asInt(startingCash))
        "go_salary" -> copy(goSalary = value.asInt(goSalary))
        "jail_fine" -> copy(jailFine = value.asInt(jailFine))
        "jail_max_turns" -> copy(jailMaxTurns = value.asInt(jailMaxTurns))
        "max_houses" -> copy(maxHouses = value.asInt(maxHouses))
        "three_doubles_to_jail" -> copy(threeDoublesToJail = value.asBool(threeDoublesToJail))
        "monopoly_doubles_rent" -> copy(monopolyDoublesRent = value.asBool(monopolyDoublesRent))
        "income_tax" -> copy(incomeTax = value.asInt(incomeTax))
        "luxury_tax" -> copy(luxuryTax = value.asInt(luxuryTax))
        else -> this
    }

    /** Baca nilai aturan; null bila kunci tak dikenal. */
    fun get(key: String): Any? = when (key) {
        "starting_cash" -> startingCash
        "go_salary" -> goSalary
        "jail_fine" -> jailFine
        "jail_max_turns" -> jailMaxTurns
        "max_houses" -> maxHouses
        "three_doubles_to_jail" -> threeDoublesToJail
        "monopoly_doubles_rent" -> monopolyDoublesRent
        "income_tax" -> incomeTax
        "luxury_tax" -> luxuryTax
        else -> null
    }

    companion object {
        /** Kunci aturan yang dikenal (untuk validasi & dokumentasi). */
        val KEYS: List<String> = listOf(
            "starting_cash", "go_salary", "jail_fine", "jail_max_turns",
            "max_houses", "three_doubles_to_jail", "monopoly_doubles_rent",
            "income_tax", "luxury_tax"
        )
    }
}

private fun Any.asInt(fallback: Int): Int = (this as? Number)?.toInt() ?: fallback
private fun Any.asBool(fallback: Boolean): Boolean = (this as? Boolean) ?: fallback
