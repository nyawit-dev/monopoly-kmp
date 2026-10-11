package com.monopoly.ui

import com.monopoly.assets.JsonAssetLoader
import com.monopoly.core.GameEnginePort
import com.monopoly.core.TileType
import com.monopoly.data.GameEngineImpl
import com.monopoly.data.GameRepository
import com.monopoly.data.diffTo
import com.monopoly.data.toSnapshotDto
import com.monopoly.net.Actions
import com.monopoly.net.HostSession
import com.monopoly.net.SnapshotDto
import korlibs.image.color.Colors
import korlibs.korge.input.onClick
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
    private val playerNames: List<String> = listOf("Pemain 1", "Pemain 2"),
    /** Null = main lokal. Guest jaringan: controller dari GameSession. */
    private val controller: GameController? = null,
    /** Host jaringan: siarkan snapshot ke guest setiap giliran. */
    private val hostSession: HostSession? = null
) : Scene() {

    override suspend fun SContainer.sceneMain() {
        val SW = BoardLayout.SCREEN_W
        val SH = BoardLayout.SCREEN_H

        // Layout responsif: ponsel (potret) vs tablet (layar lebar).
        val plan = planLayout(
            stage!!.views.actualVirtualWidth.toDouble(),
            stage!!.views.actualVirtualHeight.toDouble()
        )

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

        // ---------- Lapis papan (diskala/dipindah untuk tablet) ----------
        val boardLayer = container {
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
        }

        // Watermark tengah papan.
        val wm = text("MONOPOLY", textSize = 22.0) { color = Theme.LINE }
        wm.position(BoardLayout.ORIGIN_X + BoardLayout.TILE * 2.5 - wm.width / 2,
            BoardLayout.ORIGIN_Y + BoardLayout.TILE * 5 - 14)

        // ---------- Lapis panel (pindah ke samping saat tablet) ----------
        // Elemen dibuat langsung di panelLayer (bukan di dalam lambda) agar
        // tetap terlihat oleh renderer, tombol, dan pengamat state di bawah.
        val panelW = if (plan.isWide) plan.sidePanelW else SW
        val py = BoardLayout.PANEL_Y
        val panelLayer = container { }

        panelLayer.solidRect(panelW - 24, SH - py - 12, Theme.PANEL) { position(12, py) }
        // Judul panel pemain.
        panelLayer.text("PEMAIN", textSize = 11.0) { color = Theme.MUTED }
            .position(24, py + 12)

        // ---------- Elemen dinamis HUD (sebelum renderer: referensi list) ----------
        val moneyTexts = mutableListOf<Text>()
        val cardViews = mutableListOf<View>()
        val pionViews = mutableListOf<View>()
        val pionDots = mutableListOf<View>()
        val nameTexts = mutableListOf<Text>()

        // ---------- Kotak area dadu (tengah panel bawah) ----------
        val diceZoneY = py + 90
        panelLayer.solidRect(panelW - 48, 96.0, Theme.PANEL_LIGHT) { position(24, diceZoneY) }

        // ---------- Dadu visual ----------
        val dieSize = 72.0
        val die1 = panelLayer.createDie(panelW / 2 - dieSize - 8, diceZoneY + 12, dieSize)
        val die2 = panelLayer.createDie(panelW / 2 + 8, diceZoneY + 12, dieSize)
        die1.setValue(1)
        die2.setValue(1)

        // Pesan (baris bawah panel).
        val messageText = panelLayer.text("", textSize = 12.0) { color = Theme.PAPER }
        messageText.position(24, SH - 46)

        // ---------- Tombol aturan (muat ulang) & bayar denda ----------
        val rulesButton: View = panelLayer.solidRect((panelW - 72) / 2, 48.0, Theme.PANEL_LIGHT) { position(24, 724) }
        val jailFineButton: View = panelLayer.solidRect((panelW - 72) / 2, 48.0, Theme.PANEL_LIGHT) { position(48 + (panelW - 72) / 2, 724) }
        panelLayer.text("MUAT ULANG ATURAN", textSize = 12.0) { color = Theme.PAPER }
            .position(24 + (panelW - 72) / 4 - 52, 738)
        val jailFineLabel = panelLayer.text("BAYAR DENDA", textSize = 12.0) { color = Theme.PAPER }
        jailFineLabel.position(48 + (panelW - 72) / 2 + (panelW - 72) / 4 - 40, 738)
        jailFineButton.visible = false
        jailFineLabel.visible = false

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

        // Kartu event (Chance/Community Chest) — overlay dengan tombol OK.
        val eventCloseButton: View = solidRect(180.0, 48.0, Theme.GOLD) { position(SW / 2 - 90, 560) }
        val eventCardText: Text = text("", textSize = 14.0) { color = Theme.PAPER }
        val eventCardUI = container {
            visible = false
            solidRect(SW, SH, Colors["#000000CC"]) { position(0, 0) }
            solidRect(SW - 80, 260.0, Theme.PANEL_LIGHT) { position(40, 330) }
            addChild(eventCardText)
            text("KARTU", textSize = 16.0) { color = Theme.GOLD }.position(SW / 2 - 24, 348)
            text("OK", textSize = 18.0) { color = Theme.BG }.position(SW / 2 - 14, 572)
            addChild(eventCloseButton)
        }
        eventCardText.position(56, 400)

        // ---------- DI ----------
        val renderer = KorgeRenderer(
            dieViews = listOf(die1, die2),
            messageText = messageText,
            pionViews = pionViews,
            actionUI = actionUI,
            buyButton = buyButton,
            skipButton = skipButton,
            eventCardUI = eventCardUI,
            eventCardText = eventCardText,
            eventCardCloseButton = eventCloseButton
        )
        val ctrl: GameController = controller ?: run {
            val engine: GameEnginePort = GameEngineImpl(repo, renderer)
            engine.startGame(playerNames)
            LocalController(engine)
        }
        // Guest jaringan: alirkan snapshot/delta sesi ke GameState.
        if (ctrl is NetworkController) launch { ctrl.pumpState() }
        // Host jaringan: umumkan mulai, eksekusi Command guest, siarkan delta.
        if (hostSession != null) {
            launch { hostSession.startGame(playerNames) }
            launch {
                hostSession.commands.collect { (idx, cmd) ->
                    // Hanya eksekusi bila benar giliran pemain itu.
                    if (ctrl.state.currentPlayerIndex == idx) {
                        // tile diambil ke lokal: smart-cast untuk panggilan suspend.
                        val tile = cmd.tile
                        when (cmd.action) {
                            Actions.ROLL -> ctrl.rollDiceAndMove()
                            Actions.BUY -> ctrl.buyCurrentProperty()
                            Actions.BUILD_HOUSE ->
                                if (tile != null) ctrl.buildHouse(tile) else Unit
                            Actions.SELL_HOUSE ->
                                if (tile != null) ctrl.sellHouse(tile) else Unit
                            Actions.MORTGAGE ->
                                if (tile != null) ctrl.mortgageProperty(tile) else Unit
                            Actions.UNMORTGAGE ->
                                if (tile != null) ctrl.unmortgageProperty(tile) else Unit
                            Actions.PAY_FINE -> ctrl.payJailFine()
                            else -> Unit // SKIP: giliran berakhir otomatis
                        }
                    }
                }
            }
            // Siarkan state: penuh pertama, lalu delta hemat bandwidth.
            launch {
                var last: SnapshotDto? = null
                ctrl.observeState().collect { state ->
                    val dto = state.toSnapshotDto()
                    val prev = last
                    if (prev == null) hostSession.broadcastSnapshot(dto)
                    else hostSession.broadcastDelta(prev.turnNumber, prev.diffTo(dto, prev.turnNumber))
                    last = dto
                }
            }
        }

        // Tombol muat ulang aturan (setelah edit rules.lua).
        rulesButton.onClick {
            val ok = ctrl.reloadRules()
            messageText.text = if (ok) "Aturan dimuat ulang: ${ctrl.ruleSourceName}"
                               else "Gagal memuat aturan — tetap pakai yang lama."
        }
        // Tombol bayar denda penjara (muncul saat pemain aktif dipenjara).
        jailFineButton.onClick { stage!!.views.launch { ctrl.payJailFine() } }

        // Kartu pemain (horizontal, di bawah judul panel).
        ctrl.observeState().value.players.forEachIndexed { i, p ->
            val cardW = (panelW - 60) / 4
            val cardX = 24 + i * cardW
            val cardY = py + 32
            val card = solidRect(cardW - 8, 46.0, Theme.PANEL_LIGHT) { position(cardX, cardY) }
            cardViews.add(card)
            panelLayer.addChild(card)
            panelLayer.addChild(circle(8.0, Colors[p.colorHex]) { position(cardX + 10, cardY + 9) })
            val nameT = text(p.name.take(8), textSize = 10.0) { color = Theme.PAPER }
            nameT.position(cardX + 24, cardY + 6)
            nameTexts.add(nameT)
            panelLayer.addChild(nameT)
            val moneyT = text("Rp ${p.money}", textSize = 11.0) { color = Theme.GOLD }
            moneyT.position(cardX + 24, cardY + 24)
            moneyTexts.add(moneyT)
            panelLayer.addChild(moneyT)
        }

        // Penanda rumah/hotel + strip kepemilikan per petak properti
        // (disembunyikan; visibilitas diatur pengamat state).
        val houseMarkers = mutableMapOf<Int, View>()
        val hotelMarkers = mutableMapOf<Int, View>()
        val ownerStrips = mutableMapOf<Int, View>()
        tiles.forEach { tile ->
            if (tile.type == TileType.PROPERTY && tile.price != null) {
                val (cx, cy) = BoardLayout.center(tile.index)
                val half = BoardLayout.TILE / 2
                val house = createHouseMarker(13.0)
                house.position(cx - half + 3, cy - half - 1)
                house.visible = false
                houseMarkers[tile.index] = house
                val hotel = createHotelMarker(18.0)
                hotel.position(cx - half + 2, cy - half - 3)
                hotel.visible = false
                hotelMarkers[tile.index] = hotel
                val strip = createOwnershipStrip("#FFFFFF", 3.0)
                strip.position(cx - half, cy + half - 3)
                strip.visible = false
                ownerStrips[tile.index] = strip
            }
        }

        // Pion di papan (bidak vektor per pemain + titik warna pembeda).
        ctrl.observeState().value.players.forEachIndexed { i, p ->
            val (cx, cy) = BoardLayout.center(p.position)
            val token = createToken(
                TokenKind.values()[i % TokenKind.values().size],
                22.0
            )
            token.position(cx - 11, cy - 11 + i * 3)
            pionViews.add(token)
            pionDots.add(circle(4.0, Colors[p.colorHex]) { position(cx - 4 + i * 2, cy + 10) })
        }

        // ---------- Tombol kocok (paling bawah, besar) ----------
        val rollButton = styledButton(
            scope = stage!!.views,
            label = "KOCOK DADU",
            width = panelW - 48,
            height = 64.0,
            bg = Theme.GOLD,
            fg = Theme.BG,
            fontSize = 22.0,
            corner = 14.0
        ) {
            if (ctrl.isMyTurn) ctrl.rollDiceAndMove()
        }
        rollButton.position(24, SH - 116)
        panelLayer.addChild(rollButton)

        // ---------- Amati state untuk HUD ----------
        launch {
            ctrl.observeState().collect { state ->
                turnText.text = "GILIRAN: ${state.currentPlayer.name}"
                state.players.forEachIndexed { i, p ->
                    moneyTexts.getOrNull(i)?.text = "Rp ${p.money}"
                    // Sorot kartu pemain aktif.
                    cardViews.getOrNull(i)?.colorMul =
                        if (i == state.currentPlayerIndex) Colors.WHITE else Colors["#888888"]
                }
                // Rumah/hotel + strip kepemilikan per petak.
                houseMarkers.forEach { (idx, marker) ->
                    val h = state.houses[idx] ?: 0
                    marker.visible = h in 1..4
                }
                hotelMarkers.forEach { (idx, marker) ->
                    marker.visible = (state.houses[idx] ?: 0) >= 5
                }
                ownerStrips.forEach { (idx, strip) ->
                    val owner = state.tileOwners[idx]
                    if (owner != null) {
                        strip.visible = true
                        strip.colorMul = Colors[state.players[owner].colorHex]
                    } else {
                        strip.visible = false
                    }
                }
                // Tombol bayar denda hanya saat pemain aktif dipenjara.
                val inJail = state.currentPlayer.inJail
                jailFineButton.visible = inJail
                jailFineLabel.visible = inJail
            }
        }

        // ---------- Tata letak responsif (ponsel vs tablet) ----------
        // Elemen papan dibuat di root (butuh ctrl/state) -> pindahkan ke
        // lapis papan agar ikut diskala di layar lebar.
        houseMarkers.values.forEach { boardLayer.addChild(it) }
        hotelMarkers.values.forEach { boardLayer.addChild(it) }
        ownerStrips.values.forEach { boardLayer.addChild(it) }
        pionViews.forEach { boardLayer.addChild(it) }
        pionDots.forEach { boardLayer.addChild(it) }
        boardLayer.addChild(wm)

        fun applyLayout(p: LayoutPlan) {
            if (p.isWide) {
                // Tablet: papan kiri (diskala muat tinggi), panel samping kanan.
                boardLayer.position(p.boardOffsetX, p.boardOffsetY)
                boardLayer.scale = p.boardScale
                panelLayer.position(p.sidePanelX, 0)
            } else {
                // Ponsel: papan atas, panel bawah (tata letak asli).
                boardLayer.position(0.0, 0.0)
                boardLayer.scale = 1.0
                panelLayer.position(0.0, BoardLayout.PANEL_Y.toDouble())
            }
        }
        applyLayout(plan)

        // Terapkan ulang saat jendela diubah ukuran (putar layar).
        var lastVW = stage!!.views.actualVirtualWidth.toDouble()
        var lastVH = stage!!.views.actualVirtualHeight.toDouble()
        addUpdater { _ ->
            val vw = stage!!.views.actualVirtualWidth.toDouble()
            val vh = stage!!.views.actualVirtualHeight.toDouble()
            if (vw != lastVW || vh != lastVH) {
                lastVW = vw
                lastVH = vh
                applyLayout(planLayout(vw, vh))
            }
        }
    }
}
