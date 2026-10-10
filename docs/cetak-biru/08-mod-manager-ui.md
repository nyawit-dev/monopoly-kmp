# 08 — Mod Manager UI

Layar potret 540×960, konsisten dengan bahasa visual game (dokumen 05
`theme.json` yang aktif). Diakses dari **Menu → MOD**.

## Tata letak

```
┌──────────────────────────────┐
│ MOD                    [↻]   │  header: judul + tombol MUAT ULANG
├──────────────────────────────┤
│ ┌──────────────────────────┐ │  kartu per mod:
│ │ Pot Free Parking    ●AKTIF│ │  - nama (kiri), badge status (kanan)
│ │ v1.0 · kamu · prio 200    │ │  - versi · author · prioritas
│ │ [rules] [theme]           │ │  - chip peran
│ │ ── toggle ──  ▲ ▼         │ │  - toggle aktif + panah prioritas
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Papan Standar       ○MATI │ │  badge: AKTIF / MATI / ERROR /
│ └──────────────────────────┘ │  SEBAGIAN (degraded)
├──────────────────────────────┤
│ LOG MOD                      │  3 baris terakhir:
│ 12:03  pot-free   INFO  ...  │  [waktu] [mod] [level] pesan
│ 12:03  inti        INFO  ... │  level: INFO / WARN / ERROR
├──────────────────────────────┤
│ Folder mod: mods/            │  info lokasi + hitung mod aktif
└──────────────────────────────┘
```

## Interaksi

- **Toggle aktif** → tulis `mods/state.json` → reload otomatis. Saat di
  dalam game: papan/tema dirender ulang, state game dipertahankan
  (kecuali `tile_count` berubah → reset ke menu + pesan).
- **▲ ▼ prioritas** → ubah angka ±10 (min 0, max 999) → reload. Mod
  basis `inti` (0) tak bisa diubah.
- **↻ MUAT ULANG** → rescan + recompile semua; snackbar "Mod dimuat
  ulang (n aktif)".
- **Ketuk kartu** → panel detail slide-in:

```
┌──────────────────────────────┐
│ ← Pot Free Parking           │
│ v1.0.0 · oleh kamu           │
│                              │
│ Tema parkir dapat pot $500.  │
│                              │
│ PERAN                        │
│ [rules] [theme] [assets]     │
│                              │
│ IZIN DIMINTA                 │
│ [modify_money] [ui_message]  │
│                              │
│ DEPENDENSI                   │
│ ✓ ekonomi-inti@^1.0 (1.2.0)  │
│                              │
│ STATUS: SEBAGIAN             │
│ ⚠ rules.lua: baris 12 sintaks│  pesan actionable dari Log Mod
│                              │
│ Prioritas: 200  [▲] [▼]      │
└──────────────────────────────┘
```

## Status badge

- `AKTIF` — hijau/emas
- `SEBAGIAN` — kuning (mod jalan, sebagian peran mati)
- `MATI` — abu (dimatikan user)
- `ERROR` — merah (gagal validate/resolve; pesan di detail)

## Status kosong & galat

- Tak ada folder `mods/` atau kosong: panel kosong dengan panduan
  "Taruh folder mod di `mods/` — lihat Panduan Modder".
- Log Mod penuh ERROR: badge header jadi merah; ketuk → filter log ERROR.

## Prinsip

- Semua aksinya **selalu aman**: tak ada state tempat user bisa
  "mengunci" game — reload ulang selalu memulihkan dari disk.
- Tak pernah meminta input teks bebas (hindari salah ketik); semua lewat
  toggle & tombol.
