package com.monopoly.app

import com.monopoly.ui.MonopolyGameplayScene
import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.image.color.Colors

/**
 * Runner sebenarnya. Entry point root (Main.kt di root package) mendelegasikan
 * ke sini — KorGE Android mencari main() di root package, bukan di package ini.
 * Satu entry untuk JVM & JS (KMP). KorGE 6.x namespace: korlibs.korge.*.
 */
suspend fun runMonopoly() = Korge(
    virtualWidth = 1024,
    virtualHeight = 720,
    title = "Monopoly KMP KorGE",
    bgcolor = Colors["#F5F0E6"]
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { MonopolyGameplayScene() }
}
