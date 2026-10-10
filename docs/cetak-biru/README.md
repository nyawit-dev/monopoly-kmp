# Cetak Biru: Mod Gateway (Plugin Architecture)

Dokumen rancangan resmi sistem mod untuk Monopoli KMP. Semua keputusan
teknis di sini sudah **dikunci** (bukan opsi terbuka) — implementasi
mengikuti dokumen ini apa adanya.

## Isi

| # | Dokumen | Isi |
|---|---|---|
| 01 | [Landasan](01-landasan.md) | Prinsip, arsitektur, glosarium, non-tujuan |
| 02 | [Plugin Host](02-plugin-host.md) | Lifecycle, state, reload, state.json |
| 03 | [Manifest](03-manifest.md) | Skema mod.json, validasi, kontrak error |
| 04 | [Gerbang M](04-gerbang-m.md) | Kontrak API: event, ctx, aksi, rule registry |
| 05 | [Peran Data](05-peran-data.md) | Skema board/cards/texts/theme + override |
| 06 | [Aset](06-aset.md) | Peran aset: resolusi, fallback |
| 07 | [Sandbox & Keamanan](07-sandbox-keamanan.md) | Model izin, api_version, batas eksekusi |
| 08 | [Mod Manager UI](08-mod-manager-ui.md) | Spesifikasi tampilan & interaksi |
| 09 | [Mod Contoh](09-mod-contoh.md) | Mod lengkap semua peran |
| 10 | [Panduan Modder](10-panduan-modder.md) | Dokumentasi publik untuk pembuat mod |
| 11 | [Roadmap](11-roadmap.md) | Fase implementasi + kriteria terima |
| 12 | [Multiplayer](12-multiplayer.md) | WiFi (KorIO TCP) + Bluetooth (Kable), host-authoritative, anggaran bandwidth ramah modem (delta), protokol + discovery |
| 13 | [Tampilan Dinamis](13-tampilan-dinamis.md) | Basis virtual 540×960, breakpoint ponsel/tablet, dockedTo, ResizeComponent |

## Status

- **Fase rancangan: FINAL** — siap dieksekusi
- **Implementasi: BELUM DIMULAI** (pengembangan kode dihentikan sementara)

## Keputusan utama (ringkas)

1. Mod = **paket berperan** (rules/board/cards/texts/theme/assets), bukan satu file
2. Gerbang tunggal `M`: event bus berprioritas + `setRule/getRule` + aksi
3. **Satu Globals Lua per mod** — isolasi total, error tak menular
4. Sandbox berbasis **capability** — mod mengajukan izin, bukan diberi semua
5. Kegagalan selalu **graceful**: per-mod mati per-peran, game tak pernah crash
6. Basis = `rules.lua` bawaan (prioritas 0), mod user menumpuk di atasnya
