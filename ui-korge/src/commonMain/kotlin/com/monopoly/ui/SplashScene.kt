package com.monopoly.ui

import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.tween.*
import korlibs.korge.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import korlibs.time.milliseconds

/**
 * Splash screen: judul dengan efek misregistrasi cetak (tiga lapis tinta),
 * grid halus latar, animasi masuk, lalu lanjut otomatis / tap untuk lewati.
 */
class SplashScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        val scope = CoroutineScope(coroutineContext)
        solidRect(BoardLayout.SCREEN_W, BoardLayout.SCREEN_H, Theme.BG)

        // Judul misregistrasi: vermilion kiri-atas, biru kanan-bawah, kertas di atas.
        // Motif identitas cetak risograph (bukan dekorasi generik).
        val title = "MONOPOLY"
        val size = 62.0
        val cx = BoardLayout.SCREEN_W / 2
        val cy = 400.0
        val probe = text(title, textSize = size) { color = Theme.VERMILION }
        val w = probe.width
        probe.position(cx - w / 2 - 4, cy - 4)
        val blue = text(title, textSize = size) { color = Theme.BLUE }
        blue.position(cx - w / 2 + 4, cy + 4)
        val top = text(title, textSize = size) { color = Theme.PAPER }
        top.position(cx - w / 2, cy)

        // Animasi masuk judul.
        launch {
            top.scale = 0.85; blue.scale = 0.85; probe.scale = 0.85
            top.tween(top::scale[1.0], time = 400.milliseconds)
            blue.tween(blue::scale[1.0], time = 400.milliseconds)
            probe.tween(probe::scale[1.0], time = 400.milliseconds)
        }

        // Pemisah emas.
        solidRect(200.0, 3.0, Theme.GOLD) { position(cx - 100, 500.0) }

        // Subjudul.
        val sub = text("KOTLIN MULTIPLATFORM · KORGE EDITION", textSize = 14.0) {
            color = Theme.MUTED
        }
        sub.position(cx - sub.width / 2, 525.0)

        // Ajakan tap — animasi masuk sekali (bukan loop tak berujung).
        val tap = text("TEKAN UNTUK MEMULAI", textSize = 18.0) { color = Theme.PAPER }
        tap.position(cx - tap.width / 2, 640.0)
        launch {
            delay(900)
            tap.tween(tap::scale[1.08], time = 500.milliseconds)
            tap.tween(tap::scale[1.0], time = 500.milliseconds)
        }

        // Kredit.
        val credit = text("© 2026 NYAWIT", textSize = 12.0) { color = Theme.MUTED }
        credit.position(cx - credit.width / 2, 880.0)

        suspend fun goMenu() {
            sceneContainer.changeTo { MenuScene() }
        }

        // Tap di mana pun untuk lewati (changeTo suspend -> butuh coroutine).
        onClick { scope.launch { goMenu() } }

        // Lanjut otomatis setelah 3.8 detik.
        launch {
            delay(3800)
            goMenu()
        }
    }
}
