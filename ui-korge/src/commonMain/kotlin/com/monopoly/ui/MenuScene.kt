package com.monopoly.ui

import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.*

/**
 * Home / menu utama: pemilih jumlah pemain (2-4), tombol mulai & keluar.
 */
class MenuScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        solidRect(1024, 720, Theme.BG)

        // Panel kiri (identitas).
        solidRect(360, 720, Theme.PANEL)
        solidRect(4, 720, Theme.GOLD) { position(360, 0) }

        val logo1 = text("MONO", textSize = 66.0) { color = Theme.PAPER }
        logo1.position(48, 170)
        val logo2 = text("POLY", textSize = 66.0) { color = Theme.GOLD }
        logo2.position(48, 244)
        val tagline = text("KMP + KORGE EDITION", textSize = 15.0) { color = Theme.MUTED }
        tagline.position(48, 326)
        val version = text("v1.1 · 2026", textSize = 13.0) { color = Theme.MUTED }
        version.position(48, 672)

        // Panel kanan (menu).
        val menuTitle = text("MENU UTAMA", textSize = 30.0) { color = Theme.PAPER }
        menuTitle.position(424, 96)

        // Pemilih jumlah pemain.
        val countLabel = text("JUMLAH PEMAIN", textSize = 13.0) { color = Theme.MUTED }
        countLabel.position(424, 168)

        var selectedCount = 2
        val underlines = mutableListOf<View>()
        val labels = mutableListOf<Text>()

        val counts = listOf(2, 3, 4)
        counts.forEachIndexed { i, n ->
            container {
                solidRect(76, 60, Theme.PANEL_LIGHT)
                val t = text("$n", textSize = 26.0) {
                    color = if (n == 2) Theme.GOLD else Theme.PAPER
                }
                t.position(76 / 2 - t.width / 2, 60 / 2 - 13)
                labels.add(t)
                val ul = solidRect(44, 3, Theme.GOLD) { position(16, 68); visible = (n == 2) }
                underlines.add(ul)
                position(424 + i * 92, 196)
                onClick {
                    selectedCount = n
                    underlines.forEachIndexed { j, u -> u.visible = (j == i) }
                    labels.forEachIndexed { j, l -> l.color = if (j == i) Theme.GOLD else Theme.PAPER }
                }
            }
        }

        // Tombol mulai.
        styledButton(
            scope = stage!!.views,
            label = "MULAI PERMAINAN",
            width = 300.0,
            height = 64.0,
            bg = Theme.GOLD,
            fg = Theme.BG,
            fontSize = 21.0
        ) {
            val names = (1..selectedCount).map { "Pemain $it" }
            sceneContainer.changeTo { GameplayScene(names) }
        }.position(424, 330)

        // Tombol keluar.
        styledButton(
            scope = stage!!.views,
            label = "KELUAR",
            width = 300.0,
            height = 48.0,
            bg = Theme.PANEL_LIGHT,
            fg = Theme.PAPER,
            border = Theme.LINE,
            fontSize = 16.0
        ) {
            views.gameWindow.close()
        }.position(424, 412)

        // Info kontrol.
        val hint = text("Klik petak & tombol untuk bermain", textSize = 13.0) { color = Theme.MUTED }
        hint.position(424, 500)
    }
}
