package com.monopoly.ui

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.input.onClick
import korlibs.korge.tween.*
import korlibs.korge.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import korlibs.time.milliseconds

/**
 * Palet & komponen UI bersama — nuansa risograph zine: kertas krem,
 * ink gelap, tinta spot vermilion & biru, aksen emas.
 * Semua warna hex agar konsisten lintas platform (JVM/Android/JS).
 */
object Theme {
    val BG: RGBA = Colors["#14110D"]          // latar ink
    val PANEL: RGBA = Colors["#1E1A15"]       // panel
    val PANEL_LIGHT: RGBA = Colors["#2A251E"] // panel terang
    val PAPER: RGBA = Colors["#F5F0E6"]       // teks utama (kertas)
    val MUTED: RGBA = Colors["#9A9488"]       // teks sekunder
    val GOLD: RGBA = Colors["#D4A537"]        // aksen emas
    val VERMILION: RGBA = Colors["#C8102E"]   // tinta spot merah
    val BLUE: RGBA = Colors["#2E6FA8"]        // tinta spot biru
    val GREEN: RGBA = Colors["#3E7C4F"]
    val LINE: RGBA = Colors["#3A332A"]        // garis halus
    val TILE: RGBA = Colors["#241F19"]        // kartu petak

    /** Warna grup properti Monopoli standar. */
    fun groupColor(group: String?): RGBA = when (group?.uppercase()) {
        "BROWN" -> Colors["#8B5A2B"]
        "LIGHT_BLUE" -> Colors["#A8D8EA"]
        "PINK", "MAGENTA" -> Colors["#D63384"]
        "ORANGE" -> Colors["#E8890C"]
        "RED" -> Colors["#C8102E"]
        "YELLOW" -> Colors["#E8C547"]
        "GREEN" -> Colors["#3E7C4F"]
        "DARK_BLUE", "BLUE" -> Colors["#1E4E8C"]
        else -> LINE
    }
}

/**
 * Tombol bergaya profesional: kotak rounded dengan border opsional.
 * Umpan balik tekan: skala mengecil lalu kembali sebelum aksi dijalankan.
 * Klik memicu aksi suspend via scope yang diberikan.
 */
fun Container.styledButton(
    scope: CoroutineScope,
    label: String,
    width: Double,
    height: Double = 56.0,
    bg: RGBA = Theme.PANEL_LIGHT,
    fg: RGBA = Theme.PAPER,
    border: RGBA? = null,
    fontSize: Double = 20.0,
    corner: Double = 10.0,
    onClick: suspend () -> Unit
): Container {
    val btn = container {
        roundRect(
            korlibs.math.geom.Size2D(width, height),
            korlibs.math.geom.RectCorners(corner),
            bg,
            border ?: Colors["#4A4338"],
            height * 0.02,
            false
        )
        val labelText = text(label, textSize = fontSize) { color = fg }
        labelText.position((width - labelText.width) / 2, (height - fontSize) / 2 - 4)
    }
    btn.onClick {
        scope.launch {
            btn.tween(btn::scale[0.94], time = 70.milliseconds)
            btn.tween(btn::scale[1.0], time = 70.milliseconds)
            onClick()
        }
    }
    return btn
}
