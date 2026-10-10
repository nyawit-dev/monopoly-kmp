package com.monopoly.ui

/**
 * Layout papan Monopoli 11x11 standar: 40 petak mengelilingi persegi.
 * Petak 0 (START) di pojok kanan-bawah, berlawanan arah jarum jam.
 *
 * Resolusi virtual POTRET (ukuran HP): 540x960.
 * - Papan di ATAS: 11 x 40 = 440px, y 88..528 (+ bingkai)
 * - Panel bawah (y 540..960): kartu pemain, dadu, tombol kocok, pesan
 *
 * Mengembalikan koordinat PUSAT petak (untuk penempatan kartu & pion).
 */
object BoardLayout {
    const val SCREEN_W: Double = 540.0
    const val SCREEN_H: Double = 960.0

    const val TILE: Double = 40.0
    const val ORIGIN_X: Double = 50.0
    const val ORIGIN_Y: Double = 88.0
    /** Tinggi total papan (11 * TILE + bingkai). */
    const val BOARD_SIZE: Double = TILE * 11 + 8
    /** Y awal panel bawah (di bawah papan). */
    const val PANEL_Y: Double = BOARD_SIZE + ORIGIN_Y + 12

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
