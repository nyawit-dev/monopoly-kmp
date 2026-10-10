# 13 — Tampilan Dinamis (Ponsel & Tablet)

## Prinsip

Satu basis **virtual 540×960** (potret ponsel). Semua ukuran di scene
memakai satuan virtual; KorGE menskalakan otomatis. Perbedaan ponsel vs
tablet ditangani di **layout**, bukan di aset.

## Teknik KorGE (hasil riset docs.korge.org)

```kotlin
Korge(
    virtualWidth = 540, virtualHeight = 960,   // basis desain
    scaleMode = ScaleMode.SHOW_ALL,             // jaga rasio
    clipBorders = false,                        // tablet: isi layar lebar
    scaleAnchor = Anchor.MIDDLE_CENTER
) { … }
```

- `scaleMode = SHOW_ALL` + `clipBorders = false`: di tablet layar lebar,
  konten mengisi; area kelebihan dipakai (bukan border hitam).
- `views.actualVirtualWidth/Height`: lebar/tinggi virtual **efektif**
  (bisa > 540 di layar lebar) → dipakai untuk breakpoint.
- Elemen pinggir (HUD, tombol) memakai `dockedTo(anchor = TOP_RIGHT)`
  agar menempel sudut layar apa pun rasio.
- `ResizeComponent` di container utama: panggil ulang layout saat
  rotasi/resize.

## Breakpoint

```kotlin
val isWide = views.actualVirtualWidth >= 900   // tablet / landscape
```

| Zona | Ponsel (potret) | Tablet / landscape |
|---|---|---|
| Papan | tengah, skala 1× | tengah, sedikit diperbesar |
| Info pemain | bar bawah (horis) | panel samping kanan (vert) |
| Tombol aksi | tumpuk bawah | bar bawah tetap |
| Pesan | toast bawah | toast kanan-atas |

## Aturan matang

1. **Tak ada angka layar (nativeWidth) di logika layout** — hanya
   satuan virtual + `actualVirtual*`.
2. Semua padding/ukuran tombol kelipatan 8 virtual.
3. Tombol aksi minimum tinggi 48 virtual (sentuh nyaman).
4. Font: `TextFormat(size = 14)` dasar; judul 24; HUD 12.
5. `dockedTo` untuk HUD; `xy` untuk elemen papan (koordinat papan tetap).
6. Rotasi: `ResizeComponent` → `relayout()` membangun ulang susunan
   sesuai breakpoint.
7. Semua teks UI bisa ditimpa mod (`content/texts.json`, fase 2) —
   ukuran/tata letak tetap milik engine.

## Implementasi

- `ui-korge/.../Layout.kt` — `enum class LayoutMode { PORTRAIT, WIDE }`,
  `fun detectLayout(views): LayoutMode`, helper `layoutPad()`.
- Scene membaca mode di `sceneInit` + `ResizeComponent`; memilih
  `buildPortraitUI()` atau `buildWideUI()`.
- Papan (BoardView) tak berubah — hanya susunan sekitarnya.
