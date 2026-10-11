package com.monopoly.ui

import com.monopoly.core.ModInfo
import com.monopoly.core.ModStatus
import com.monopoly.data.ModHost
import korlibs.image.color.RGBA
import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manajer Mod (cetak biru dokumen 08) - layout POTRET (540x960):
 * daftar mod terpasang dari ModHost, toggle aktif/nonaktif (ditulis ke
 * mods/state.json lalu reload otomatis), tombol MUAT ULANG, panel Log Mod,
 * dan tombol kembali ke menu utama.
 *
 * Semua akses ModHost dibungkus runCatching: folder mods/ yang hilang atau
 * rusak jatuh ke status kosong, game tak pernah macet karena mod.
 *
 * Catatan platform: ModHost ada di core-data/jvmMain dan dipanggil dari sini
 * mengikuti pola yang sama dengan GameplayScene (target rilis = fat jar JVM).
 */
class ModManagerScene(
    /** Host mod opsional; null = host bersama (dibaca dari folder mods/). */
    private val host: ModHost? = null
) : Scene() {

    /** Host bersama agar Lua tak dikompilasi ulang setiap kali layar dibuka. */
    companion object {
        val sharedHost: ModHost? by lazy { runCatching { ModHost(File("mods")) }.getOrNull() }
    }

    private val modHost: ModHost? get() = host ?: sharedHost

    // ---------- referensi view (diisi di sceneMain, dipakai refresh) ----------
    private var subtitle: Text? = null
    private var statusLine: Text? = null
    private var emptyCard: Container? = null
    private var emptyTitle: Text? = null
    private var emptyBody: Text? = null
    private var footer: Text? = null
    private val rows = mutableListOf<Row>()
    private val logLines = mutableListOf<Text>()

    private var maxRows = 6
    private var cardX = 24.0
    private var cx = 270.0

    /** View satu baris mod; dibuat sekali, isinya diperbarui saat refresh. */
    private class Row {
        var card: Container? = null
        var nameText: Text? = null
        var metaText: Text? = null
        var statusText: Text? = null
        var toggleLabel: Text? = null
        var info: ModInfo? = null
    }

    override suspend fun SContainer.sceneMain() {
        val SW = BoardLayout.SCREEN_W
        val SH = BoardLayout.SCREEN_H
        val views = stage!!.views
        cx = SW / 2
        cardX = 24.0
        maxRows = 6
        solidRect(SW, SH, Theme.BG)

        val cardW = SW - 48
        val cardH = 84.0
        val rowGap = 10.0
        val rowTop = 116.0
        val statusX = cardX + cardW - 104
        val toggleX = cardX + cardW - 14 - 132
        val toggleW = 132.0
        val toggleH = 32.0

        // ---------- Header ----------
        solidRect(SW, 56.0, Theme.PANEL)
        solidRect(SW, 2.0, Theme.GOLD) { position(0, 56) }
        text("MANAJER MOD", textSize = 20.0) { color = Theme.GOLD }.position(16, 16)

        subtitle = text("Folder mod: mods/", textSize = 12.0) { color = Theme.MUTED }
            .also { it.position(16, 70) }
        statusLine = text("Memuat...", textSize = 12.0) { color = Theme.MUTED }
            .also { it.position(SW - 16 - 260, 70) }

        // ---------- Status kosong ----------
        emptyCard = container {
            solidRect(cardW, 180.0, Theme.PANEL)
            val t1 = text("BELUM ADA MOD", textSize = 18.0) { color = Theme.PAPER }
            t1.position(cardX + 16, rowTop + 24)
            emptyTitle = t1
            val t2 = text("", textSize = 12.0) { color = Theme.MUTED }
            t2.position(cardX + 16, rowTop + 58)
            emptyBody = t2
            visible = false
        }

        // ---------- Baris mod (view dibuat sekali, isi diperbarui) ----------
        rows.clear()
        for (i in 0 until maxRows) {
            val row = Row()
            val y = rowTop + i * (cardH + rowGap)
            val card = container {
                solidRect(cardW, cardH, Theme.PANEL)
                val name = text("", textSize = 16.0) { color = Theme.PAPER }
                name.position(cardX + 14, y + 8)
                row.nameText = name
                val status = text("", textSize = 13.0) { color = Theme.MUTED }
                status.position(statusX, y + 10)
                row.statusText = status
                val meta = text("", textSize = 10.0) { color = Theme.MUTED }
                meta.position(cardX + 14, y + 34)
                row.metaText = meta
                container {
                    roundRect(
                        korlibs.math.geom.Size2D(toggleW, toggleH),
                        korlibs.math.geom.RectCorners(10.0),
                        Theme.PANEL_LIGHT,
                        Theme.LINE,
                        toggleH * 0.02,
                        false
                    )
                    val label = text("NYALAKAN", textSize = 12.0) { color = Theme.PAPER }
                    label.position((toggleW - label.width) / 2, 8.0)
                    row.toggleLabel = label
                    position(toggleX, y + 42)
                    onClick {
                        val info = row.info ?: return@onClick
                        val h = modHost ?: return@onClick
                        val next = info.status == ModStatus.DISABLED
                        views.launch {
                            val err = runCatching {
                                withContext(Dispatchers.IO) { h.setEnabled(info.id, next) }
                            }.exceptionOrNull()
                            statusLine?.text = if (err == null) {
                                "${info.name} " + (if (next) "dinyalakan" else "dimatikan")
                            } else {
                                "Gagal menyimpan state.json: ${err.message}"
                            }
                            refresh()
                        }
                    }
                }
                visible = false
            }
            row.card = card
            rows.add(row)
        }

        // ---------- Panel Log Mod ----------
        text("LOG MOD", textSize = 12.0) { color = Theme.GOLD }.position(16, 700)
        solidRect(cardW, 132.0, Theme.PANEL) { position(cardX, 724) }
        logLines.clear()
        for (i in 0 until 4) {
            val t = text("", textSize = 9.0) { color = Theme.MUTED }
            t.position(cardX + 10, 736 + i * 26)
            logLines.add(t)
        }

        // ---------- Footer ----------
        footer = text("", textSize = 11.0) { color = Theme.MUTED }

        styledButton(
            scope = views,
            label = "MUAT ULANG",
            width = 132.0,
            height = 36.0,
            bg = Theme.GOLD,
            fg = Theme.BG,
            fontSize = 13.0,
            corner = 10.0
        ) {
            val h = modHost
            if (h == null) {
                statusLine?.text = "Folder mods/ tidak bisa dibaca"
                refresh()
            } else {
                val err = withContext(Dispatchers.IO) {
                    runCatching { h.reload() }.exceptionOrNull()
                }
                val n = runCatching { h.mods.value.count { it.status.isActiveish() } }.getOrDefault(0)
                statusLine?.text = if (err == null) {
                    "Mod dimuat ulang ($n aktif)"
                } else {
                    "Gagal memuat ulang: ${err.message}"
                }
                refresh()
            }
        }.position(SW - 16 - 132, 10)

        styledButton(
            scope = views,
            label = "KEMBALI",
            width = cardW,
            height = 48.0,
            bg = Theme.PANEL_LIGHT,
            fg = Theme.PAPER,
            border = Theme.LINE,
            fontSize = 16.0,
            corner = 12.0
        ) {
            sceneContainer.changeTo { MenuScene() }
        }.position(24, 892)

        // ---------- isi awal + pengamat state ----------
        refresh()
        launch {
            modHost?.mods?.collect { refresh() }
        }
        launch {
            modHost?.log?.collect { refresh() }
        }
    }

    /** Baca ulang daftar mod + log dari ModHost, lalu perbarui semua view. */
    private fun refresh() {
        val h = modHost
        val list = if (h == null) emptyList() else runCatching { h.mods.value }.getOrElse { emptyList() }
        val log = if (h == null) emptyList() else runCatching { h.log.value }.getOrElse { emptyList() }
        val shown = list.take(maxRows)
        val active = list.count { it.status.isActiveish() }
        val errored = list.any { it.status == ModStatus.ERROR }
        val broken = h == null

        // Status kosong.
        emptyCard?.visible = broken || list.isEmpty()
        emptyTitle?.color = if (broken) Theme.VERMILION else Theme.PAPER
        emptyTitle?.text = if (broken) "FOLDER MODS TIDAK BISA DIBACA" else "BELUM ADA MOD"
        emptyBody?.text = if (broken) {
            "Manajer mod tidak dapat dimuat. Permainan tetap berjalan tanpa mod."
        } else {
            "Taruh folder mod di mods/ - lihat panduan modder di mods/README.md"
        }

        // Baris mod.
        for (i in 0 until maxRows) {
            val row = rows.getOrNull(i) ?: continue
            val info = shown.getOrNull(i)
            if (info == null) {
                row.card?.visible = false
                row.info = null
                continue
            }
            row.info = info
            row.card?.visible = true
            row.nameText?.text = info.name.take(22)
            val err = info.error
            row.metaText?.text = buildString {
                append("v").append(info.version).append(" - ").append(info.author.take(16))
                append(" - prio ").append(info.priority)
                if (info.roles.isNotEmpty()) {
                    append(" - [").append(info.roles.joinToString("] [")).append("]")
                }
            }
            if (err != null) {
                row.metaText?.text = err.take(52)
            }
            row.metaText?.color = if (err != null) Theme.VERMILION else Theme.MUTED
            row.statusText?.text = info.status.label()
            row.statusText?.color = info.status.badgeColor()
            row.toggleLabel?.text = if (info.status == ModStatus.DISABLED) "NYALAKAN" else "MATIKAN"
        }

        if (list.size > maxRows) {
            statusLine?.text = "Tampil $maxRows dari ${list.size} mod"
        }
        subtitle?.color = if (errored) Theme.VERMILION else Theme.MUTED

        // Log Mod (4 baris terakhir).
        val last = log.takeLast(4)
        for (i in 0 until logLines.size) {
            val line = last.getOrNull(i)
            val t = logLines[i]
            if (line == null) {
                t.visible = false
                continue
            }
            t.visible = true
            t.text = "${line.modId.take(12)}  ${line.level}  ${line.message}".take(62)
            t.color = when (line.level) {
                "ERROR" -> Theme.VERMILION
                "WARN" -> Theme.GOLD
                else -> Theme.MUTED
            }
        }

        val f = footer ?: return
        f.text = "Folder mod: mods/state.json - $active dari ${list.size} mod aktif"
        f.position(cx - f.width / 2, 866)
    }
}

private fun ModStatus.isActiveish(): Boolean =
    this == ModStatus.ACTIVE || this == ModStatus.ACTIVE_DEGRADED

private fun ModStatus.label(): String = when (this) {
    ModStatus.ACTIVE -> "AKTIF"
    ModStatus.ACTIVE_DEGRADED -> "SEBAGIAN"
    ModStatus.DISABLED -> "MATI"
    ModStatus.ERROR -> "ERROR"
}

private fun ModStatus.badgeColor(): RGBA = when (this) {
    ModStatus.ACTIVE -> Theme.GREEN
    ModStatus.ACTIVE_DEGRADED -> Theme.GOLD
    ModStatus.DISABLED -> Theme.MUTED
    ModStatus.ERROR -> Theme.VERMILION
}
