# 06 — Peran Aset

## Struktur

```
mods/<id>/assets/
├── tokens/       # sprite pion pemain (per nama/warna)
├── dice/         # sprite dadu (opsional; default: dadu digambar prosedural)
├── backgrounds/  # latar: splash, menu, board
└── audio/        # sfx & musik (fase 4b, lihat bawah)
```

- Butuh capability `asset_override` di manifest.
- Format yang didukung: PNG (token/dice/background). Audio: OGG/MP3.

## Konvensi penamaan

| Slot | Nama file | Ukuran |
|---|---|---|
| Token pemain | `tokens/p1.png` … `p6.png` | kotak, transparan |
| Dadu | `dice/d1.png` … `d6.png` | kotak; fallback prosedural |
| Latar splash | `backgrounds/splash.png` | 540×960 (atau diskalakan) |
| Latar menu | `backgrounds/menu.png` | 540×960 |
| Latar papan | `backgrounds/board.png` | 540×960 |
| Audio | `audio/<kunci>.ogg` | kunci: `dice`, `buy`, `jail`, `coin`, `lose` |

## Resolusi (dikunci)

1. Cari slot di mod berprioritas **tertinggi** yang punya file itu.
2. Tak ada mod → aset bawaan Kotlin/prosedural.
3. File ada tapi gagal dimuat (rusak/format salah) → Log Mod + jatuh ke
   langkah 2 (**tidak crash**).
4. Token pemain: pemain ke-i pakai `tokens/p<i+1>.png` dari mod prioritas
   tinggi; sebagian slot boleh kosong (campuran aset mod + prosedural sah).

## Batas v1

- Tak ada animasi aset (frame sequence) — sprite statis + tween KorGE.
- Audio masuk fase 4b; bila belum ada mod audio, game tetap senyap (tak
  ada aset audio bawaan yang wajib).
- Tak ada modifikasi font eksternal; `theme.json.typography.family`
  dibatasi keluarga yang tersedia di platform (fallback sans-serif).
