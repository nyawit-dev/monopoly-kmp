package com.monopoly.ui

import com.monopoly.assets.JsonAssetLoader
import com.monopoly.core.GameEnginePort
import com.monopoly.core.TileType
import com.monopoly.data.GameEngineImpl
import com.monopoly.data.GameRepository
import korlibs.image.color.Colors
import korlibs.korge.scene.Scene
import korlibs.korge.view.*
import kotlinx.coroutines.launch

/**
 * Layar gameplay profesional:
 * - header bar (judul + indikator giliran/fase)
 * - papan 40 petak: kartu, chip warna grup properti, harga
 * - panel HUD kanan: kartu pemain (uang), kotak dadu, tombol lempar, pesan
 * - dialog aksi (beli / lewati)
 * Pion digerakkan engine via KorgeRenderer; HUD mengamati StateFlow.
 */
class GameplayScene(
    private val playerNames: List<String> = listOf("Pemain 1", "Pemain 2")
) : Scene() {

    override suspend fun SContainer.sceneMain() {
        // ---------- Data ----------
        val tiles = JsonAssetLoader().loadBoardConfig()
        val repo = GameRepository(tiles)

        // ---------- Latar ----------
        solidRect(1024, 720, Theme.BG)

        // ---------- Header ----------
        solidRect(1024, 64, Theme.PANEL)
        solidRect(1024, 2, Theme.GOLD) { position(0, 64) }
        text("MONOPOLY", textSize = 26.0) { color = Theme.GOLD }.position(24, 18)
        val turnText = text("", textSize = 15.0) { color = Theme.PAPER }
        turnText.position(760, 22)
        val phaseText = text("", textSize = 12.0) { color = Theme.MUTED }
        phaseText.position(760, 44)

        // ---------- Papan ----------
        // Bingkai papan.
        solidRect(
            BoardLayout.TILE * 11 + 8, BoardLayout.TILE * 11 + 8, Theme.PANEL
        ) { position(BoardLayout.ORIGIN_X - 4, BoardLayout.ORIGIN_Y - 4) }

        tiles.forEach { tile ->
            val (cx, cy) = BoardLayout.center(tile.index)
            val x = cx - BoardLayout.TILE / 2
            val y = cy - BoardLayout.TILE / 2
            val w = BoardLayout.TILE - 2
            // Kartu petak.
            solidRect(w, w, if (tile.isCorner) Theme.PANEL_LIGHT else Theme.TILE) { position(x, y) }
            when {
                tile.type == TileType.PROPERTY && tile.colorGroup != null -> {
                    // Chip warna grup di atas kartu.
                    solidRect(w, 8.0, Theme.groupColor(tile.colorGroup)) { position(x, y) }
                    // Harga di bawah.
                    text("${tile.price}", textSize = 9.0) { color = Theme.MUTED }
                        .position(x + 4, y + w - 13)
                }
                tile.isCorner -> {
                    val label = when (tile.index) {
                        0 -> "START"
                        10 -> "JAIL"
                        20 -> "PARKIR"
                        30 -> "KE-JAIL"
                        else -> ""
                    }
                    text(label, textSize = 9.0) { color = Theme.GOLD }
                        .position(x + 4, y + w - 14)
                }
                else -> {
                    val sym = when (tile.type) {
                        TileType.RAILROAD -> "S"
                        TileType.UTILITY -> "U"
                        else -> "?"
                    }
                    text(sym, textSize = 15.0) { color = Theme.MUTED }
                        .position(x + w / 2 - 4, y + w / 2 - 8)
                }
            }
        }

        // Watermark tengah papan.
        val wm = text("MONOPOLY", textSize = 34.0) { color = Theme.LINE }
        wm.position(304.0 - wm.width / 2, 360.0 - 20)

        // ---------- Panel HUD kanan ----------
        val px = 600.0
        val pw = 1024.0 - px - 16
        solidRect(pw, 720.0 - 96 - 16, Theme.PANEL) { position(px, 96.0) }

        // Judul panel.
        text("PEMAIN", textSize = 14.0) { color = Theme.MUTED }.position(px + 20, 116)

        // ---------- Elemen dinamis ----------
        val moneyTexts = mutableListOf<Text>()
        val pionViews = mutableListOf<View>()

        // Kotak dadu.
        solidRect(180, 120, Theme.PANEL_LIGHT) { position(px + (pw - 180) / 2, 370) }
        val die1Text = text("--", textSize = 44.0) { color = Theme.PAPER }
        val die2Text = text("--", textSize = 44.0) { color = Theme.PAPER }
        die1Text.position(px + (pw - 180) / 2 + 30, 398)
        die2Text.position(px + (pw - 180) / 2 + 100, 398)

        // Pesan.
        val messageText = text("", textSize = 13.0) { color = Theme.PAPER }
        messageText.position(px + 20, 600)

        // Dialog aksi (tombol dibuat di luar agar bisa direferensikan renderer).
        val buyButton: View = solidRect(150, 48, Theme.GREEN) { position(px + 56, 470) }
        val skipButton: View = solidRect(150, 48, Theme.PANEL_LIGHT) { position(px + 226, 470) }
        val actionUI = container {
            visible = false
            solidRect(1024.0, 720.0, Colors["#000000CC"]) { position(0, 0) }
            solidRect(pw - 40, 200.0, Theme.PANEL_LIGHT) { position(px + 20, 380.0) }
            text("BELI PROPERTI?", textSize = 18.0) { color = Theme.PAPER }
                .position(px + 56, 410)
            text("BELI", textSize = 18.0) { color = Theme.PAPER }.position(px + 112, 484)
            text("LEWATI", textSize = 18.0) { color = Theme.PAPER }.position(px + 278, 484)
            addChild(buyButton)
            addChild(skipButton)
        }

        // ---------- DI: engine + renderer ----------
        val renderer = KorgeRenderer(
            dieTexts = listOf(die1Text, die2Text),
            messageText = messageText,
            pionViews = pionViews,
            actionUI = actionUI,
            buyButton = buyButton,
            skipButton = skipButton
        )
        val engine: GameEnginePort = GameEngineImpl(repo, renderer)

        // ---------- Mulai game ----------
        engine.startGame(playerNames)

        // Kartu pemain (HUD). Penanda identitas = lingkaran sejajar pion di
        // papan (warna sama), bukan stripe dekoratif.
        engine.observeState().value.players.forEachIndexed { i, p ->
            val cardY = 140.0 + i * 52
            solidRect(pw - 40, 48.0, Theme.PANEL_LIGHT) { position(px + 20, cardY) }
            circle(11.0, Colors[p.colorHex]) { position(px + 33, cardY + 13) }
            text(p.name, textSize = 15.0) { color = Theme.PAPER }.position(px + 52, cardY + 6)
            val moneyT = text("Rp ${p.money}", textSize = 13.0) { color = Theme.GOLD }
            moneyT.position(px + 52, cardY + 26)
            moneyTexts.add(moneyT)
        }

        // Pion di papan.
        engine.observeState().value.players.forEachIndexed { i, p ->
            val (cx, cy) = BoardLayout.center(p.position)
            val pion = circle(13.0, Colors[p.colorHex]) { position(cx - 13, cy - 13 + i * 6) }
            pionViews.add(pion)
        }

        // Amati state untuk HUD (giliran, fase, uang).
        launch {
            engine.observeState().collect { state ->
                turnText.text = "GILIRAN: ${state.currentPlayer.name}"
                phaseText.text = state.phase.name
                state.players.forEachIndexed { i, p ->
                    moneyTexts.getOrNull(i)?.text = "Rp ${p.money}"
                }
            }
        }

        // Tombol lempar.
        styledButton(
            scope = stage!!.views,
            label = "LEMPAR DADU",
            width = 240.0,
            height = 56.0,
            bg = Theme.GOLD,
            fg = Theme.BG,
            fontSize = 18.0
        ) {
            engine.rollDiceAndMove()
        }.position(px + (pw - 240) / 2, 510)
    }
}
