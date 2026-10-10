package com.monopoly.ui

import korlibs.image.color.Colors
import korlibs.korge.view.*
import korlibs.math.geom.RectCorners
import korlibs.math.geom.Size2D

/**
 * Dadu visual: kotak putih rounded dengan pip gelap sesuai nilai 1-6.
 * Nilai bisa diubah dinamis (dipakai animasi kocok oleh KorgeRenderer).
 * Posisi dasar disimpan agar bisa di-restore setelah animasi goyang.
 */
class Die internal constructor(
    val view: Container,
    private val pips: List<Circle>,
    val baseX: Double,
    val baseY: Double
) {
    /** Tampilkan pip sesuai nilai (1-6). */
    fun setValue(v: Int) {
        // Indeks pip pada grid 3x3 (0..8):
        // 0 1 2
        // 3 4 5
        // 6 7 8
        val on: Set<Int> = when (v.coerceIn(1, 6)) {
            1 -> setOf(4)
            2 -> setOf(0, 8)
            3 -> setOf(0, 4, 8)
            4 -> setOf(0, 2, 6, 8)
            5 -> setOf(0, 2, 4, 6, 8)
            else -> setOf(0, 2, 3, 5, 6, 8) // 6
        }
        pips.forEachIndexed { i, pip -> pip.visible = i in on }
    }
}

/**
 * Buat dadu di dalam [parent]. Ukuran default 64px.
 * Posisi (x, y) = sudut kiri-atas kotak dadu.
 */
fun Container.createDie(x: Double, y: Double, size: Double = 64.0): Die {
    val view = container { position(x, y) }
    // Badan dadu: kotak putih rounded dengan border tipis.
    view.roundRect(
        Size2D(size, size),
        RectCorners(size * 0.18),
        Colors.WHITE,
        Colors["#D8D2C4"],
        size * 0.04,
        true
    )
    // Sembilan posisi pip (grid 3x3); visibilitas diatur setValue().
    val r = size * 0.085
    val pips = mutableListOf<Circle>()
    for (row in 0..2) {
        for (col in 0..2) {
            val pip = view.circle(r, Colors["#1A1714"])
            val fx = 0.24 + col * 0.26 // 0.24, 0.50, 0.76
            val fy = 0.24 + row * 0.26
            pip.position(fx * size - r, fy * size - r)
            pip.visible = false
            pips.add(pip)
        }
    }
    return Die(view, pips, x, y)
}
