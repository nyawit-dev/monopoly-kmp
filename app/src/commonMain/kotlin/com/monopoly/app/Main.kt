package com.monopoly.app

import com.monopoly.ui.MonopolyGameplayScene
import com.soywiz.korge.Korge
import com.soywiz.korge.scene.sceneContainer

/**
 * Entry point. Menyatukan DI di dalam scene (repository -> engine ->
 * renderer) dan menjalankan blok KorGE. Satu main untuk JVM & JS (KMP).
 */
suspend fun main() = Korge(
    virtualWidth = 1024,
    virtualHeight = 720,
    title = "Monopoly KMP KorGE",
    bgcolor = com.soywiz.korim.color.Colors["#F5F0E6"]
) {
    val sceneContainer = sceneContainer()
    sceneContainer.changeTo { MonopolyGameplayScene() }
}
