package com.monopoly.ui

/**
 * Layout papan Monopoli 11x11 standar: 40 petak mengelilingi persegi.
 * Petak 0 (START) di pojok kanan-bawah, berlawanan arah jarum jam.
 * Mengembalikan koordinat PUSAT petak (untuk penempatan kartu & pion).
 */
object BoardLayout {
    const val TILE: Double = 48.0
    const val ORIGIN_X: Double = 40.0
    const val ORIGIN_Y: Double = 96.0

    fun center(index: Int): Pair<Double, Double> {
        val i = ((index % 40) + 40) % 40
        val gx: Double
        val gy: Double
        when {
            i == 0 -> { gx = 10.0; gy = 10.0 }               // START (pojok kanan-bawah)
            i in 1..9 -> { gx = (10 - i).toDouble(); gy = 10.0 }   // sisi bawah, ke kiri
            i == 10 -> { gx = 0.0; gy = 10.0 }               // JAIL (pojok kiri-bawah)
            i in 11..19 -> { gx = 0.0; gy = (10 - (i - 10)).toDouble() } // sisi kiri, ke atas
            i == 20 -> { gx = 0.0; gy = 0.0 }                // FREE_PARKING (pojok kiri-atas)
            i in 21..29 -> { gx = (i - 20).toDouble(); gy = 0.0 }   // sisi atas, ke kanan
            i == 30 -> { gx = 10.0; gy = 0.0 }               // GO_TO_JAIL (pojok kanan-atas)
            else -> { gx = 10.0; gy = (i - 30).toDouble() }  // sisi kanan, ke bawah
        }
        val cx = ORIGIN_X + gx * TILE + TILE / 2
        val cy = ORIGIN_Y + gy * TILE + TILE / 2
        return Pair(cx, cy)
    }
}
