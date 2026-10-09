package com.monopoly.ui

import com.monopoly.assets.JsonAssetLoader
import com.monopoly.core.GameEnginePort
import com.monopoly.data.GameEngineImpl
import com.monopoly.data.GameRepository
import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.*
import korlibs.image.color.Colors
import kotlinx.coroutines.launch

/**
 * Scene gameplay. Menyambungkan DI: repository -> engine -> renderer.
 * KorGE hanya host; aturan tetap di core-data. KorGE 6.x (namespace korlibs.*).
 */
class MonopolyGameplayScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        // Aset papan (aturan harga) dimuat dari JSON commonMain.
        val assetLoader = JsonAssetLoader()
        val tiles = assetLoader.loadBoardConfig()

        // UI elemen.
        val diceText = text("0", textSize = 48.0) {
            position(880, 560)
        }
        val messageText = text("Tekan Lempar untuk mulai", textSize = 18.0) {
            position(40, 600)
        }

        // Tombol LEMPAR: container berisi solidRect + label (posisi manual).
        val rollButton = container {
            solidRect(180, 56, Colors["#C8102E"]) { position(0, 0) }
            text("LEMPAR", textSize = 22.0) {
                color = Colors.WHITE
                position(55, 15)
            }
            position(860, 620)
        }

        // Referensi tombol aksi (dibuat sebagai View, bukan anak container
        // agar bisa langsung dipakai renderer tanpa traversal anak).
        var buyButton: View = solidRect(160, 56, Colors["#2E7D32"]) { position(360, 300) }
        var skipButton: View = solidRect(160, 56, Colors["#B0B0B0"]) { position(540, 300) }

        // Panel aksi: background gelap + label tombol (tombol sudah dibuat di atas,
        // ditambahkan ke panel supaya tampil di atas overlay).
        val actionUI = container {
            visible = false
            solidRect(1024, 640, Colors["#000000AA"]) { position(0, 0) }
            text("BELI", textSize = 22.0) { color = Colors.WHITE; position(435, 315) }
            text("LEWATI", textSize = 22.0) { color = Colors.WHITE; position(615, 315) }
            addChild(buyButton)
            addChild(skipButton)
        }

        // Pion (satu per pemain, dibuat saat start).
        val pionViews = mutableListOf<View>()

        // DI + renderer + engine.
        val repo = GameRepository(tiles)
        val renderer = KorgeRenderer(
            diceText = diceText,
            messageText = messageText,
            pionViews = pionViews,
            actionUI = actionUI,
            buyButton = buyButton,
            skipButton = skipButton
        )
        val engine: GameEnginePort = GameEngineImpl(repo, renderer)

        // Mulai dengan 2 pemain default (bisa diganti UI input nama).
        engine.startGame(listOf("Pemain 1", "Pemain 2"))
        // Buat pion setelah pemain ada.
        engine.observeState().value.players.forEachIndexed { i, p ->
            val c = Colors[p.colorHex]
            val pion = solidRect(28, 28, c) { position(950.0, 600.0 + i * 8) }
            pionViews.add(pion)
        }

        // Tombol lempar -> panggil engine di coroutine scene.
        rollButton.onClick {
            launch {
                engine.rollDiceAndMove()
            }
        }
    }
}
