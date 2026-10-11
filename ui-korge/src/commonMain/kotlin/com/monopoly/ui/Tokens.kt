package com.monopoly.ui

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import korlibs.math.geom.RectCorners
import korlibs.math.geom.Size2D

/**
 * Tipe pion (token) pemain gaya Monopoli klasik, nama Indonesia.
 * Divalisasikan oleh [createToken] menjadi gambar vektor sederhana.
 */
enum class TokenKind { TOPI, MOBIL, KAPAL, SEPATU, ANJING, KUCING, JARUM, KERETA }

/**
 * Buat token pemain di dalam container ini.
 * Bentuk & warna dibedakan per [kind]; ukuran proporsional terhadap [size]
 * (panjang token = [size] x [size], asal koordinat di kiri-atas).
 *
 * Semua token digambar dengan primitif KorGE: solidRect, roundRect,
 * circle, ellipse, dan graphics (segitiga) supaya bisa dipakai di semua platform.
 */
fun Container.createToken(kind: TokenKind, size: Double = 32.0): Container {
    val token = container {}
    token.drawTokenShape(kind, size)
    return token
}

private fun Container.drawTokenShape(kind: TokenKind, size: Double) {
    when (kind) {
        TokenKind.TOPI -> drawTopHat(size)
        TokenKind.MOBIL -> drawCar(size)
        TokenKind.KAPAL -> drawShip(size)
        TokenKind.SEPATU -> drawShoe(size)
        TokenKind.ANJING -> drawDog(size)
        TokenKind.KUCING -> drawCat(size)
        TokenKind.JARUM -> drawThimble(size)
        TokenKind.KERETA -> drawTrain(size)
    }
}

/** Warna klasik token Monopoli + warna tema. */
private val INK: RGBA = Colors["#1A1714"]
private val HAT_BODY: RGBA = Colors["#1A1714"]
private val HAT_BAND: RGBA = Theme.GOLD
private val CAR_RED: RGBA = Theme.VERMILION
private val WHEEL: RGBA = Colors["#14110D"]
private val GLASS: RGBA = Colors["#CFE3EE"]
private val HULL: RGBA = Colors["#5A4632"]
private val SAIL: RGBA = Theme.PAPER
private val SHOE_BODY: RGBA = Colors["#8B5A2B"]
private val SOLE: RGBA = Colors["#14110D"]
private val DOG_TAN: RGBA = Colors["#D9B98A"]
private val DOG_SPOT: RGBA = Colors["#8B5A2B"]
private val CAT_ORANGE: RGBA = Colors["#E8890C"]
private val CAT_GRAY: RGBA = Colors["#7A7368"]
private val THIMBLE_BODY: RGBA = Colors["#C9CDD2"]
private val TRAIN_BODY: RGBA = Colors["#14110D"]
private val TRAIN_CABIN: RGBA = Colors["#3E7C4F"]
private val TRAIN_CATCHER: RGBA = Theme.VERMILION

/** Segitiga terisi: (x1,y1) puncak, (x2,y2) kiri-bawah, (x3,y3) kanan-bawah. */
private fun Container.triangle(
    x1: Double, y1: Double,
    x2: Double, y2: Double,
    x3: Double, y3: Double,
    color: RGBA
) {
    graphics {
        fill(color) {
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(x3, y3)
            close()
        }
    }
}

/** TOPI: silinder gelap, brim lebar (ellipse gepeng), pita emas tipis di dasar. */
private fun Container.drawTopHat(s: Double) {
    // Brim: ellipse lebar di bagian bawah.
    ellipse(Size2D(s * 1.0, s * 0.32), HAT_BODY) { position(s * 0.5, s * 0.78) }
    // Badan topi: solidRect vertikal.
    solidRect(s * 0.5, s * 0.56, HAT_BODY) { position(s * 0.25, s * 0.2) }
    // Atas topi sedikit melebar (rounded tipis).
    solidRect(s * 0.5, s * 0.06, HAT_BODY) { position(s * 0.25, s * 0.14) }
    // Pita emas di pangkal badan.
    solidRect(s * 0.5, s * 0.07, HAT_BAND) { position(s * 0.25, s * 0.69) }
}

/** MOBIL: badan merah rounded, kabin, kaca, dua roda hitam. */
private fun Container.drawCar(s: Double) {
    // Badan utama.
    roundRect(
        Size2D(s * 0.82, s * 0.34),
        RectCorners(s * 0.1),
        CAR_RED,
        WHEEL,
        s * 0.03,
        true
    ) { position(s * 0.09, s * 0.46) }
    // Kabin (atap) lebih kecil, agak ke belakang.
    roundRect(
        Size2D(s * 0.46, s * 0.26),
        RectCorners(s * 0.09),
        CAR_RED,
        WHEEL,
        s * 0.03,
        true
    ) { position(s * 0.22, s * 0.24) }
    // Kaca depan.
    solidRect(s * 0.26, s * 0.14, GLASS) { position(s * 0.36, s * 0.29) }
    // Roda kiri & kanan.
    circle(s * 0.11, WHEEL) { position(s * 0.26, s * 0.74) }
    circle(s * 0.11, WHEEL) { position(s * 0.66, s * 0.74) }
}

/** KAPAL: lambung coklat, layar putih segitiga, tiang. */
private fun Container.drawShip(s: Double) {
    // Lambung bawah: trapezoid pendek (trapesium via segitiga + solidRect).
    solidRect(s * 0.62, s * 0.16, HULL) { position(s * 0.19, s * 0.62) }
    // Bagian lambung yang menyempit ke bawah.
    triangle(
        s * 0.19, s * 0.78,
        s * 0.81, s * 0.78,
        s * 0.5, s * 0.94,
        HULL
    )
    // Tiang segitiga: layar kiri & kanan dari satu batang.
    solidRect(s * 0.05, s * 0.56, HULL) { position(s * 0.475, s * 0.2) }
    triangle(
        s * 0.45, s * 0.22,
        s * 0.45, s * 0.66,
        s * 0.1, s * 0.66,
        SAIL
    )
    triangle(
        s * 0.55, s * 0.22,
        s * 0.55, s * 0.66,
        s * 0.9, s * 0.66,
        SAIL
    )
}

/** SEPATU: tubuh coklat rounded, sol gelap, tumit kecil di belakang. */
private fun Container.drawShoe(s: Double) {
    // Tumit kecil di belakang bawah.
    solidRect(s * 0.1, s * 0.16, SOLE) { position(s * 0.76, s * 0.72) }
    // Badan sepatu: rounded memanjang.
    roundRect(
        Size2D(s * 0.74, s * 0.4),
        RectCorners(s * 0.14),
        SHOE_BODY,
        Colors["#5A3A1A"],
        s * 0.025,
        true
    ) { position(s * 0.1, s * 0.3) }
    // Sol gelap di bawah.
    solidRect(s * 0.78, s * 0.1, SOLE) { position(s * 0.1, s * 0.66) }
    // Bagian depan sepatu (ujung membulat) sedikit lebih terang.
    circle(s * 0.14, SHOE_BODY) { position(s * 0.16, s * 0.5) }
}

/** ANJING: badan tan rounded, kepala lingkaran, telinga, ekor, mata & hidung. */
private fun Container.drawDog(s: Double) {
    // Ekor: segitiga kecil di belakang atas.
    triangle(
        s * 0.2, s * 0.5,
        s * 0.06, s * 0.22,
        s * 0.26, s * 0.26,
        DOG_SPOT
    )
    // Badan.
    roundRect(
        Size2D(s * 0.56, s * 0.44),
        RectCorners(s * 0.14),
        DOG_TAN,
        Colors["#8B5A2B"],
        s * 0.03,
        true
    ) { position(s * 0.16, s * 0.36) }
    // Bintik coklat di punggung.
    circle(s * 0.12, DOG_SPOT) { position(s * 0.44, s * 0.42) }
    // Kepala.
    circle(s * 0.17, DOG_TAN) { position(s * 0.6, s * 0.24) }
    // Telinga menjuntai.
    circle(s * 0.07, DOG_SPOT) { position(s * 0.72, s * 0.16) }
    // Mata.
    circle(s * 0.035, INK) { position(s * 0.68, s * 0.22) }
    // Hidung.
    circle(s * 0.045, INK) { position(s * 0.74, s * 0.3) }
}

/** KUCING: badan bulat abu-abu, kepala, dua telinga segitiga, ekor, mata. */
private fun Container.drawCat(s: Double) {
    // Ekor melengkung (arc via ellipse tipis digeser).
    ellipse(Size2D(s * 0.44, s * 0.18), CAT_GRAY) { position(s * 0.02, s * 0.28) }
    // Badan.
    circle(s * 0.22, CAT_GRAY) { position(s * 0.14, s * 0.4) }
    // Kepala.
    circle(s * 0.18, CAT_GRAY) { position(s * 0.5, s * 0.2) }
    // Telinga kiri & kanan (segitiga).
    triangle(
        s * 0.42, s * 0.24,
        s * 0.4, s * 0.04,
        s * 0.56, s * 0.16,
        CAT_GRAY
    )
    triangle(
        s * 0.6, s * 0.24,
        s * 0.62, s * 0.04,
        s * 0.76, s * 0.16,
        CAT_GRAY
    )
    // Belang oranye di badan.
    solidRect(s * 0.1, s * 0.1, CAT_ORANGE) { position(s * 0.2, s * 0.52) }
    // Mata.
    circle(s * 0.035, INK) { position(s * 0.56, s * 0.2) }
    circle(s * 0.035, INK) { position(s * 0.68, s * 0.2) }
}

/** JARUM: silinder kecil abu-abu dengan titik-titik (cekungan) di atas. */
private fun Container.drawThimble(s: Double) {
    // Badan silinder: rounded vertikal.
    roundRect(
        Size2D(s * 0.34, s * 0.68),
        RectCorners(s * 0.08),
        THIMBLE_BODY,
        Colors["#8B9196"],
        s * 0.025,
        true
    ) { position(s * 0.33, s * 0.18) }
    // Cekungan (dots) di puncak.
    circle(s * 0.05, Colors["#8B9196"]) { position(s * 0.44, s * 0.24) }
    circle(s * 0.05, Colors["#8B9196"]) { position(s * 0.53, s * 0.24) }
    circle(s * 0.05, Colors["#8B9196"]) { position(s * 0.485, s * 0.33) }
    // Gelang bawah yang lebih gelap.
    solidRect(s * 0.34, s * 0.06, Colors["#8B9196"]) { position(s * 0.33, s * 0.8) }
}

/** KERETA: mesin hitam, kabin hijau, cerobong, penangkap sapi merah, dua roda. */
private fun Container.drawTrain(s: Double) {
    // Kabin belakang (hijau).
    roundRect(
        Size2D(s * 0.36, s * 0.3),
        RectCorners(s * 0.05),
        TRAIN_CABIN,
        WHEEL,
        s * 0.03,
        true
    ) { position(s * 0.54, s * 0.28) }
    // Cerobong asap.
    solidRect(s * 0.14, s * 0.16, TRAIN_BODY) { position(s * 0.16, s * 0.28) }
    solidRect(s * 0.18, s * 0.05, TRAIN_BODY) { position(s * 0.14, s * 0.24) }
    // Badan mesin utama (hitam).
    roundRect(
        Size2D(s * 0.46, s * 0.32),
        RectCorners(s * 0.06),
        TRAIN_BODY,
        Colors["#3A332A"],
        s * 0.03,
        true
    ) { position(s * 0.2, s * 0.44) }
    // Penangkap sapi (cowcatcher) merah di depan.
    triangle(
        s * 0.12, s * 0.7,
        s * 0.2, s * 0.76,
        s * 0.12, s * 0.88,
        TRAIN_CATCHER
    )
    // Roda besar (belakang) & kecil (depan).
    circle(s * 0.11, WHEEL) { position(s * 0.52, s * 0.68) }
    circle(s * 0.08, WHEEL) { position(s * 0.28, s * 0.7) }
}
