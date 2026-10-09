package com.monopoly.app

import com.monopoly.ui.MonopolyGameplayScene
import korlibs.korge.Korge
import korlibs.korge.scene.sceneContainer
import korlibs.image.color.Colors

/**
 * Entry point. Menyatukan DI di dalam scene (repository -> engine ->
 * renderer) dan menjalankan blok KorGE. Satu main untuk JVM & JS (KMP).
 * KorGE 6.x namespace: korlibs.korge.* (bukan com.soywiz.korge).
 */
suspend fun main() = Korge(
    virtualWidth = 1024,
    virtualHeight = 720,
    title = "Monopoly KMP KorGE",
    bgcolor = Colors["#F5F0E6"]
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { MonopolyGameplayScene() }
}
