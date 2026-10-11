package com.monopoly.ui

import korlibs.image.color.Colors
import korlibs.image.color.RGBA
import korlibs.korge.view.*
import korlibs.math.geom.Angle
import korlibs.math.geom.RectCorners
import korlibs.math.geom.Size2D

/** Warna khusus marker: hijau gelap untuk atap rumah, kuning untuk jendela hotel. */
private val ROOF_DARK: RGBA = Colors["#26512F"]
private val WINDOW_YELLOW: RGBA = Colors["#E8C547"]

/** Kartu atap penjuru papan: Kesempatan (Chance) atau Dana Umum (Chest). */
enum class CardKind { CHANCE, CHEST }

/**
 * Rumah kecil (house) untuk petak properti.
 *
 * Susunan: atap segitiga dibentuk dari persegi yang dirotasi 45 derajat
 * (KorGE 6 tidak punya primitif poligon), lalu badan kotak hijau di atasnya.
 * Setengah bawah belah ketupat tertutup badan rumah sehingga terlihat menyatu.
 *
 * Titik asal = sudut kiri-atas kotak imajiner sebesar [size] x [size].
 *
 * @param size lebar/tinggi total marker dalam piksel (default 16px, pas di satu petak papan)
 * @param color warna badan rumah (default [Theme.GREEN]); atap selalu hijau lebih gelap
 */
fun Container.createHouseMarker(size: Double = 16.0, color: RGBA = Theme.GREEN): Container {
    val view = container {}
    // Ukuran dipilih agar total tinggi marker = size tepat:
    // tinggi atap terlihat (size * 0.42) + tinggi badan (size * 0.58).
    val bodyW = size * 0.84
    val bodyH = size * 0.58
    val roofH = size * 0.42
    val bodyY = size - bodyH

    // Atap: persegi dirotasi 45 derajat menjadi belah ketupat. Kotak pembatas
    // belah ketupat selalu sama lebar dan tingginya (S = d * sqrt(2)), dan
    // separuh bawah tertutup badan rumah, jadi segitiga terlihat punya alas S
    // dan tinggi S/2. Maka S = 2 * roofH, selebar badan rumah.
    val s = 2.0 * roofH
    val d = s * 0.7071 // sqrt(2) / 2
    view.solidRect(d, d, ROOF_DARK) {
        // Titik tengah belah ketupat di (size / 2, roofH): puncak tepat y=0.
        position(size / 2 - d / 2, roofH - d / 2)
        rotation = Angle.QUARTER
    }

    // Badan rumah.
    view.solidRect(bodyW, bodyH, color) {
        position(size / 2 - bodyW / 2, bodyY)
    }

    // Pintu kecil di tengah badan.
    val doorW = size * 0.16
    val doorH = size * 0.28
    view.solidRect(doorW, doorH, ROOF_DARK) {
        position(size / 2 - doorW / 2, size - doorH)
    }

    return view
}

/**
 * Hotel: gedung merah lebih tinggi dari rumah, dengan jendela kuning dan
 * pita atap gelap. Dibuat sengaja lebih besar agar jelas berbeda dari [createHouseMarker].
 *
 * Titik asal = sudut kiri-atas kotak imajiner sebesar [size] x [size].
 *
 * @param size lebar/tinggi total marker dalam piksel (default 24px)
 */
fun Container.createHotelMarker(size: Double = 24.0): Container {
    val view = container {}
    val bodyW = size * 0.86
    val bodyH = size * 0.62
    val bodyY = size - bodyH

    // Pita atap: strip gelap lebih lebar dari badan, menempel di atasnya.
    val roofH = size * 0.14
    view.solidRect(bodyW * 1.08, roofH, ROOF_DARK) {
        position(size / 2 - bodyW * 1.08 / 2, bodyY - roofH)
    }

    // Badan gedung: kotak rounded merah.
    view.roundRect(
        Size2D(bodyW, bodyH),
        RectCorners(size * 0.06),
        Theme.VERMILION,
        ROOF_DARK,
        size * 0.03,
        true
    ) {
        position(size / 2 - bodyW / 2, bodyY)
    }

    // Jendela kuning: dua baris.
    val win = size * 0.13
    val cols = 3
    val gap = (bodyW - cols * win) / (cols + 1)
    val rowY = bodyY + bodyH * 0.20
    for (row in 0..1) {
        for (col in 0 until cols) {
            view.solidRect(win, win, WINDOW_YELLOW) {
                position(
                    size / 2 - bodyW / 2 + gap + col * (win + gap),
                    rowY + row * win * 1.9
                )
            }
        }
    }

    return view
}

/**
 * Bagian belakang kartu event (Chance / Community Chest).
 *
 * Kartu krem rounded dengan bingkai berwarna: jingga untuk CHANCE,
 * biru untuk CHEST. Simbol besar ("?" atau "$") di tengah kartu.
 *
 * Titik asal = sudut kiri-atas kartu.
 *
 * @param kind jenis kartu, menentukan warna bingkai dan simbol
 * @param width lebar kartu (default 44px; tinggi mengikuti rasio 44x62)
 */
fun Container.createCardBack(kind: CardKind, width: Double = 44.0): Container {
    val height = width * 62.0 / 44.0
    val view = container {}

    val (border, symbol) = when (kind) {
        CardKind.CHANCE -> Pair(Theme.GOLD, "?")
        CardKind.CHEST -> Pair(Theme.BLUE, "$")
    }

    view.roundRect(
        Size2D(width, height),
        RectCorners(width * 0.14),
        Theme.PAPER,
        border,
        width * 0.06,
        true
    )

    val fontSize = width * 0.62
    val label = view.text(symbol, textSize = fontSize) { color = border }
    label.position(
        (width - label.width) / 2,
        (height - fontSize) / 2 + fontSize * 0.12
    )

    return view
}

/**
 * Strip penanda kepemilikan petak: batang tipis berwarna pemain,
 * ditempel di tepi papan (di atas chip warna grup).
 *
 * @param ownerColorHex warna pemain dalam format hex "#RRGGBB"
 * @param height tinggi strip (default 4px), lebar tetap 3px
 */
fun Container.createOwnershipStrip(ownerColorHex: String, height: Double = 4.0): Container {
    val view = container {}
    view.solidRect(3.0, height, Colors[ownerColorHex])
    return view
}
