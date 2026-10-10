package com.monopoly.ui

import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.*

/**
 * Home / menu utama — layout POTRET (540x960):
 * logo judul, pemilih jumlah pemain (2-4), tombol mulai & keluar.
 */
class MenuScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        val SW = BoardLayout.SCREEN_W
        val SH = BoardLayout.SCREEN_H
        solidRect(SW, SH, Theme.BG)

        // Judul logo (dua baris, misregistrasi tipis).
        val cx = SW / 2
        val logo1 = text("MONO", textSize = 54.0) { color = Theme.PAPER }
        logo1.position(cx - logo1.width / 2, 180)
        val logo2 = text("POLY", textSize = 54.0) { color = Theme.GOLD }
        logo2.position(cx - logo2.width / 2, 240)
        val tagline = text("KOTLIN MULTIPLATFORM · KORGE EDITION", textSize = 12.0) {
            color = Theme.MUTED
        }
        tagline.position(cx - tagline.width / 2, 312)

        // Pemisah emas.
        solidRect(160.0, 3.0, Theme.GOLD) { position(cx - 80, 350) }

        // Pemilih jumlah pemain.
        val countLabel = text("JUMLAH PEMAIN", textSize = 13.0) { color = Theme.MUTED }
        countLabel.position(cx - countLabel.width / 2, 400)

        var selectedCount = 2
        val underlines = mutableListOf<View>()
        val labels = mutableListOf<Text>()

        val counts = listOf(2, 3, 4)
        val chipW = 76.0
        val totalW = chipW * 3 + 16 * 2
        counts.forEachIndexed { i, n ->
            container {
                solidRect(chipW, 60.0, Theme.PANEL_LIGHT)
                val t = text("$n", textSize = 26.0) {
                    color = if (n == 2) Theme.GOLD else Theme.PAPER
                }
                t.position(chipW / 2 - t.width / 2, 60.0 / 2 - 13)
                labels.add(t)
                val ul = solidRect(44, 3, Theme.GOLD) { position(16, 68); visible = (n == 2) }
                underlines.add(ul)
                position(cx - totalW / 2 + i * (chipW + 16), 428)
                onClick {
                    selectedCount = n
                    underlines.forEachIndexed { j, u -> u.visible = (j == i) }
                    labels.forEachIndexed { j, l -> l.color = if (j == i) Theme.GOLD else Theme.PAPER }
                }
            }
        }

        // Tombol mulai (lebar penuh, besar).
        styledButton(
            scope = stage!!.views,
            label = "MULAI PERMAINAN",
            width = SW - 48,
            height = 68.0,
            bg = Theme.GOLD,
            fg = Theme.BG,
            fontSize = 22.0,
            corner = 14.0
        ) {
            val names = (1..selectedCount).map { "Pemain $it" }
            sceneContainer.changeTo { GameplayScene(names) }
        }.position(24, 560)

        // Tombol keluar.
        styledButton(
            scope = stage!!.views,
            label = "KELUAR",
            width = SW - 48,
            height = 52.0,
            bg = Theme.PANEL_LIGHT,
            fg = Theme.PAPER,
            border = Theme.LINE,
            fontSize = 16.0,
            corner = 12.0
        ) {
            views.gameWindow.close()
        }.position(24, 648)

        // Info kontrol.
        val hint = text("Pilih jumlah pemain, lalu mulai permainan", textSize = 12.0) {
            color = Theme.MUTED
        }
        hint.position(cx - hint.width / 2, 740)

        // Versi.
        val version = text("v1.3 · 2026", textSize = 11.0) { color = Theme.MUTED }
        version.position(cx - version.width / 2, SH - 60)
    }
}
