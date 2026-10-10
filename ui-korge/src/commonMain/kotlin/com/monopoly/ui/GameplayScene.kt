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
 * Layar gameplay — layout POTRET (540x960) untuk HP:
 * - papan 40 petak di ATAS (chip warna grup + harga + nama singkat)
 * - panel bawah: kartu pemain (horizontal), dadu visual (pip),
 *   tombol kocok besar, baris pesan
 * - dialog beli muncul sebagai overlay tengah
 * Pion digerakkan engine via KorgeRenderer; HUD mengamati StateFlow.
 */
class GameplayScene(
    private val playerNames: List<String> = listOf("Pemain 1", "Pemain 2")
) : Scene() {

    override suspend fun SContainer.sceneMain() {
        val SW = BoardLayout.SCREEN_W
        val SH = BoardLayout.SCREEN_H

        // ---------- Data ----------
        val tiles = JsonAssetLoader().loadBoardConfig()
        val repo = GameRepository(tiles)

        // ---------- Latar ----------
        solidRect(SW, SH, Theme.BG)

        // ---------- Header ----------
        solidRect(SW, 56.0, Theme.PANEL)
        solidRect(SW, 2.0, Theme.GOLD) { position(0, 56) }
        text("MONOPOLY", textSize = 20.0) { color = Theme.GOLD }.position(16, 16)
        val turnText = text("", textSize = 13.0) { color = Theme.PAPER }
        turnText.position(SW - 16 - 200, 20)

        // ---------- Papan (atas) ----------
        solidRect(
            BoardLayout.BOARD_SIZE, BoardLayout.BOARD_SIZE, Theme.PANEL
        ) { position(BoardLayout.ORIGIN_X - 4, BoardLayout.ORIGIN_Y - 4) }

        tiles.forEach { tile ->
            val (cx, cy) = BoardLayout.center(tile.index)
            val x = cx - BoardLayout.TILE / 2
            val y = cy - BoardLayout.TILE / 2
            val w = BoardLayout.TILE - 2
            solidRect(w, w, if (tile.isCorner) Theme.PANEL_LIGHT else Theme.TILE) { position(x, y) }
            when {
                tile.type == TileType.PROPERTY && tile.colorGroup != null -> {
                    // Chip warna grup.
                    solidRect(w, 7.0, Theme.groupColor(tile.colorGroup)) { position(x, y) }
                    // Harga.
                    text("${tile.price}", textSize = 8.0) { color = Theme.MUTED }
                        .position(x + 2, y + w - 11)
                }
                tile.isCorner -> {
                    val label = when (tile.index) {
                        0 -> "START"; 10 -> "JAIL"; 20 -> "PARKIR"; 30 -> "JAIL"; else -> ""
                    }
                    text(label, textSize = 8.0) { color = Theme.GOLD }
                        .position(x + 2, y + w - 11)
                }
                else -> {
                    val sym = when (tile.type) {
                        TileType.RAILROAD -> "R"
                        TileType.UTILITY -> "U"
                        else -> "?"
                    }
                    text(sym, textSize = 13.0) { color = Theme.MUTED }
                        .position(x + w / 2 - 4, y + w / 2 - 7)
                }
            }
        }

        // Watermark tengah papan.
        val wm = text("MONOPOLY", textSize = 22.0) { color = Theme.LINE }
        wm.position(BoardLayout.ORIGIN_X + BoardLayout.TILE * 2.5 - wm.width / 2,
            BoardLayout.ORIGIN_Y + BoardLayout.TILE * 5 - 14)

        // ---------- Panel bawah ----------
        val py = BoardLayout.PANEL_Y
        solidRect(SW - 24, SH - py - 12, Theme.PANEL) { position(12, py) }

        // Judul panel pemain.
        text("PEMAIN", textSize = 11.0) { color = Theme.MUTED }.position(24, py + 12)

        // ---------- Elemen dinamis HUD (sebelum renderer: referensi list) ----------
        val moneyTexts = mutableListOf<Text>()
        val cardViews = mutableListOf<View>()
        val pionViews = mutableListOf<View>()
        val nameTexts = mutableListOf<Text>()

        // ---------- Kotak area dadu (tengah panel bawah) ----------
        val diceZoneY = py + 90
        solidRect(SW - 48, 96.0, Theme.PANEL_LIGHT) { position(24, diceZoneY) }

        // ---------- Dadu visual ----------
        val dieSize = 72.0
        val die1 = createDie(SW / 2 - dieSize - 8, diceZoneY + 12, dieSize)
        val die2 = createDie(SW / 2 + 8, diceZoneY + 12, dieSize)
        die1.setValue(1)
        die2.setValue(1)

        // Pesan (baris bawah panel).
        val messageText = text("", textSize = 12.0) { color = Theme.PAPER }
        messageText.position(24, SH - 46)

        // Dialog aksi (tombol dibuat di luar agar bisa direferensikan renderer).
        val buyButton: View = solidRect(140, 44, Theme.GREEN) { position(SW / 2 - 150, 640) }
        val skipButton: View = solidRect(140, 44, Theme.PANEL_LIGHT) { position(SW / 2 + 10, 640) }
        val actionUI = container {
            visible = false
            solidRect(SW, SH, Colors["#000000CC"]) { position(0, 0) }
            solidRect(SW - 80, 200.0, Theme.PANEL_LIGHT) { position(40, 400) }
            text("BELI PROPERTI?", textSize = 18.0) { color = Theme.PAPER }
                .position(SW / 2 - 70, 430)
            text("BELI", textSize = 16.0) { color = Theme.PAPER }.position(SW / 2 - 110, 654)
            text("LEWATI", textSize = 16.0) { color = Theme.PAPER }.position(SW / 2 + 48, 654)
            addChild(buyButton)
            addChild(skipButton)
        }

        // ---------- DI ----------
        val renderer = KorgeRenderer(
            dieViews = listOf(die1, die2),
            messageText = messageText,
            pionViews = pionViews,
            actionUI = actionUI,
            buyButton = buyButton,
            skipButton = skipButton
        )
        val engine: GameEnginePort = GameEngineImpl(repo, renderer)

        // ---------- Mulai game ----------
        engine.startGame(playerNames)

        // Kartu pemain (horizontal, di bawah judul panel).
        engine.observeState().value.players.forEachIndexed { i, p ->
            val cardW = (SW - 60) / 4
            val cardX = 24 + i * cardW
            val cardY = py + 32
            val card = solidRect(cardW - 8, 46.0, Theme.PANEL_LIGHT) { position(cardX, cardY) }
            cardViews.add(card)
            circle(8.0, Colors[p.colorHex]) { position(cardX + 10, cardY + 9) }
            val nameT = text(p.name.take(8), textSize = 10.0) { color = Theme.PAPER }
            nameT.position(cardX + 24, cardY + 6)
            nameTexts.add(nameT)
            val moneyT = text("Rp ${p.money}", textSize = 11.0) { color = Theme.GOLD }
            moneyT.position(cardX + 24, cardY + 24)
            moneyTexts.add(moneyT)
        }

        // Pion di papan.
        engine.observeState().value.players.forEachIndexed { i, p ->
            val (cx, cy) = BoardLayout.center(p.position)
            val pion = circle(9.0, Colors[p.colorHex]) { position(cx - 9, cy - 9 + i * 4) }
            pionViews.add(pion)
        }

        // ---------- Tombol kocok (paling bawah, besar) ----------
        styledButton(
            scope = stage!!.views,
            label = "KOCOK DADU",
            width = SW - 48,
            height = 64.0,
            bg = Theme.GOLD,
            fg = Theme.BG,
            fontSize = 22.0,
            corner = 14.0
        ) {
            engine.rollDiceAndMove()
        }.position(24, SH - 116)

        // ---------- Amati state untuk HUD ----------
        launch {
            engine.observeState().collect { state ->
                turnText.text = "GILIRAN: ${state.currentPlayer.name}"
                state.players.forEachIndexed { i, p ->
                    moneyTexts.getOrNull(i)?.text = "Rp ${p.money}"
                    // Sorot kartu pemain aktif.
                    cardViews.getOrNull(i)?.colorMul =
                        if (i == state.currentPlayerIndex) Colors.WHITE else Colors["#888888"]
                }
            }
        }
    }
}
