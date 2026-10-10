package com.monopoly.app

import com.monopoly.ui.SplashScene
import korlibs.image.color.Colors
import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer

/**
 * Runner sebenarnya. Entry point root (Main.kt di root package) mendelegasikan
 * ke sini — KorGE Android mencari main() di root package, bukan di package ini.
 * Alur: Splash -> Menu -> Gameplay. Resolusi virtual POTRET (ukuran HP):
 * 540x960, layout papan di atas + panel aksi di bawah.
 * KorGE 6.x namespace: korlibs.korge.*.
 */
suspend fun runMonopoly() = Korge(
    virtualWidth = 540,
    virtualHeight = 960,
    title = "Monopoly KMP KorGE",
    bgcolor = Colors["#14110D"]
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { SplashScene() }
}
