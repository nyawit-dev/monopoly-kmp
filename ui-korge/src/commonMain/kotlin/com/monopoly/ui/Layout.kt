package com.monopoly.ui

import korlibs.korge.view.*

/**
 * Toolkit tata letak responsif untuk scene KorGE, sesuai cetak biru 13
 * (Tampilan Dinamis: Ponsel & Tablet).
 *
 * Prinsip: satu basis virtual 540x960 (potret ponsel) dengan
 * `scaleMode = SHOW_ALL` dan `clipBorders = false`. Di layar lebar,
 * `views.actualVirtualWidth` bisa > 540 dan kelebihan ruang dipakai untuk
 * panel samping, bukan border hitam. Perbedaan ponsel vs tablet ditangani
 * di LAYOUT, bukan di aset (cetak biru 13, bagian Prinsip).
 *
 * Semua angka di berkas ini memakai satuan virtual dan tipe [Double]
 * (aturan matang 1 dan 2: tak ada nativeWidth, padding kelipatan 8).
 */

/**
 * Kelas layar hasil breakpoint.
 *
 * - [PORTRAIT]: basis potret 540x960, papan di atas, panel info di bawah
 *   (baris horisontal).
 * - [WIDE]: tablet atau layar lanskap. Papan di kiri, panel samping kanan
 *   untuk HUD/kontrol pemain.
 */
enum class ScreenClass {
    PORTRAIT,
    WIDE
}

/**
 * Lebar virtual minimum agar layar dianggap [ScreenClass.WIDE]
 * (tablet-class atau lanskap).
 *
 * Cetak biru 13 memakai 900 sebagai contoh; nilai 720 dipakai di sini
 * sebagai ambang tablet-class yang lebih sesuai agar ponsel lanskap
 * (mis. 960x540) tetap dapat panel samping. Ubah satu konstanta ini jika
 * kalibrasi ulang diperlukan.
 */
const val WIDE_BREAKPOINT: Double = 720.0

/** Jarak tepi (gutter) untuk susunan [ScreenClass.WIDE]; kelipatan 8. */
private const val WIDE_EDGE_MARGIN: Double = 24.0

/**
 * Deteksi kelas layar dari ukuran virtual EFEKTIF.
 *
 * Aturan breakpoint (murni, mudah diuji):
 * - `actualVirtualWidth >= 720` -> [ScreenClass.WIDE] (tablet-class atau
 *   viewport lanskap, lebar lebih besar dari basis potret 540).
 * - selain itu -> [ScreenClass.PORTRAIT].
 *
 * Panggil dengan `views.actualVirtualWidth` / `views.actualVirtualHeight`,
 * BUKAN `views.nativeWidth` (aturan matang 1 cetak biru 13).
 *
 * ```kotlin
 * val screenClass = detectScreenClass(
 *     actualVirtualWidth = views.actualVirtualWidth,
 *     actualVirtualHeight = views.actualVirtualHeight
 * )
 * ```
 */
fun detectScreenClass(actualVirtualWidth: Double, actualVirtualHeight: Double): ScreenClass =
    if (actualVirtualWidth >= WIDE_BREAKPOINT) ScreenClass.WIDE else ScreenClass.PORTRAIT

/**
 * Rencana geometri satu frame, dihitung murni dari ukuran virtual efektif.
 *
 * Koordinat papan memakai sistem yang sama dengan [BoardLayout] (sudut
 * kiri-atas papan pada [boardOffsetX], [boardOffsetY], dengan [boardScale]
 * sebagai pengali terhadap koordinat papan standar).
 *
 * - [PORTRAIT]: papan skala 1.0 di area atas, dipusatkan horizontal;
 *   tak ada panel samping ([sidePanelW] = 0.0), info pemain memakai bar
 *   bawah seperti susunan [BoardLayout.PANEL_Y].
 * - [WIDE]: papan di kiri, diskalakan agar tingginya pas dengan viewport
 *   (dibatasi 0.5x..2.0x), dan panel samping kanan untuk HUD/kontrol.
 *
 * @param boardScale pengali ukuran papan terhadap ukuran virtual standar
 * @param boardOffsetX x sudut kiri-atas papan
 * @param boardOffsetY y sudut kiri-atas papan
 * @param sidePanelX x awal panel samping kanan (0.0 saat PORTRAIT)
 * @param sidePanelW lebar panel samping kanan (0.0 saat PORTRAIT)
 * @param isWide true bila kelas layar [ScreenClass.WIDE]
 */
data class LayoutPlan(
    val boardScale: Double,
    val boardOffsetX: Double,
    val boardOffsetY: Double,
    val sidePanelX: Double,
    val sidePanelW: Double,
    val isWide: Boolean
)

/**
 * Hitung [LayoutPlan] untuk ukuran virtual efektif saat ini.
 *
 * Cara pakai di scene (panggil di `sceneMain` untuk susunan awal, dan
 * ulangi saat resize):
 *
 * ```kotlin
 * fun relayout() {
 *     val plan = planLayout(views.actualVirtualWidth, views.actualVirtualHeight)
 *     boardContainer.scale = plan.boardScale
 *     boardContainer.position(plan.boardOffsetX, plan.boardOffsetY)
 *     if (plan.isWide) {
 *         sidePanel.position(plan.sidePanelX, 0.0)
 *         sidePanel.size(plan.sidePanelW, views.actualVirtualHeight)
 *     }
 * }
 * ```
 *
 * Catatan rotasi (aturan matang 6, cetak biru 13): jangan hitung resize
 * sendiri. Pasang satu `ResizeComponent` (`korlibs.korge.scene`) pada
 * container root scene, lalu di dalam callback-nya panggil `relayout()`
 * seperti di atas. Karena [planLayout] murni, ia bisa dipanggil berulang
 * tanpa efek samping dan aman dipakai untuk rebuild susunan saat kelas
 * layar berubah (mis. ponsel diputar: PORTRAIT -> WIDE -> PORTRAIT).
 */
fun planLayout(actualVirtualWidth: Double, actualVirtualHeight: Double): LayoutPlan {
    val isWide = detectScreenClass(actualVirtualWidth, actualVirtualHeight) == ScreenClass.WIDE
    if (!isWide) {
        // PORTRAIT: papan tetap ukuran desain di area atas. X memakai
        // BoardLayout.ORIGIN_X (bukan hasil tengah murni) agar penempatan
        // berbasis plan selalu selaras dengan BoardLayout.center() per petak.
        val boardScale = 1.0
        val boardOffsetX = BoardLayout.ORIGIN_X
        val boardOffsetY = BoardLayout.ORIGIN_Y
        return LayoutPlan(
            boardScale = boardScale,
            boardOffsetX = boardOffsetX,
            boardOffsetY = boardOffsetY,
            sidePanelX = 0.0,
            sidePanelW = 0.0,
            isWide = false
        )
    }
    // WIDE: papan di kiri, diskalakan pas tinggi viewport (dibatasi),
    // panel samping kanan memakai sisa lebar.
    val boardScale = ((actualVirtualHeight - 2.0 * WIDE_EDGE_MARGIN) / BoardLayout.BOARD_SIZE)
        .coerceIn(0.5, 2.0)
    val boardWidth = BoardLayout.BOARD_SIZE * boardScale
    val boardHeight = BoardLayout.BOARD_SIZE * boardScale
    val boardOffsetX = WIDE_EDGE_MARGIN
    val boardOffsetY = ((actualVirtualHeight - boardHeight) / 2.0).coerceAtLeast(0.0)
    val sidePanelX = boardOffsetX + boardWidth + WIDE_EDGE_MARGIN
    val sidePanelW = (actualVirtualWidth - sidePanelX - WIDE_EDGE_MARGIN).coerceAtLeast(0.0)
    return LayoutPlan(
        boardScale = boardScale,
        boardOffsetX = boardOffsetX,
        boardOffsetY = boardOffsetY,
        sidePanelX = sidePanelX,
        sidePanelW = sidePanelW,
        isWide = true
    )
}

/** Sudut penempelan untuk [Container.dockedTo]. */
enum class DockCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

/**
 * Tempelkan view ini ke sudut induknya (aturan matang 5, cetak biru 13:
 * HUD memakai `dockedTo`, elemen papan tetap memakai koordinat papan).
 *
 * Posisi dihitung dari `parent.width` / `parent.height`, jadi elemen
 * menempel sudut viewport apa pun rasionya. Untuk container root scene,
 * parent berukuran virtual efektif.
 *
 * ```kotlin
 * val turnBadge = solidRect(180.0, 40.0, Theme.PANEL_LIGHT) { /* isi HUD */ }
 * turnBadge.dockedTo(DockCorner.TOP_RIGHT)
 *
 * val moneyBadge = text("Rp 1.500")
 * moneyBadge.dockedTo(DockCorner.TOP_LEFT)
 * ```
 *
 * Reflow saat resize: simpan referensi elemen HUD, lalu panggil ulang
 * `dockedTo` yang sama di dalam callback `ResizeComponent` (lihat
 * [planLayout]). Karena hanya membaca ukuran induk, pemanggilan ulang
 * aman dan tak menumpuk state.
 */
fun View.dockedTo(corner: DockCorner, margin: Double = 16.0) {
    val p = parent ?: return
    val x = when (corner) {
        DockCorner.TOP_LEFT, DockCorner.BOTTOM_LEFT -> margin
        DockCorner.TOP_RIGHT, DockCorner.BOTTOM_RIGHT ->
            (p.width - width - margin).coerceAtLeast(margin)
    }
    val y = when (corner) {
        DockCorner.TOP_LEFT, DockCorner.TOP_RIGHT -> margin
        DockCorner.BOTTOM_LEFT, DockCorner.BOTTOM_RIGHT ->
            (p.height - height - margin).coerceAtLeast(margin)
    }
    position(x, y)
}

/**
 * Alias khusus [Container] (implementasi ada di [View.dockedTo], berlaku
 * juga untuk Text/SolidRect). Dipakai scene untuk badge HUD apa pun.
 */
fun Container.dockedTo(corner: DockCorner, margin: Double = 16.0) =
    (this as View).dockedTo(corner, margin)
