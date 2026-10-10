# 07 — Sandbox & Keamanan

## Model izin (capability-based)

Mod **mengajukan** capability di manifest; pemakai melihatnya di Mod
Manager. Aksi yang butuh capability tak diizinkan → **di-drop saat
runtime** + warning Log Mod (bukan error saat load).

| Capability | Membuka |
|---|---|
| `read_state` | `M.getState`, `M.getTile` |
| `modify_money` | `giveMoney`, `takeMoney` |
| `move_players` | `movePlayer` |
| `jail_control` | `sendToJail`, `releaseFromJail`, `grantJailCard` |
| `draw_cards` | `drawCard` |
| `ui_message` | `message` |
| `ui_overlay` | `scripts/ui.lua` (fase 5) |
| `asset_override` | peran `assets` |

`M.log` dan `M.on` selalu bebas. `setRule/getRule` bebas (dianggap
bagian dari peran rules).

## Batas eksekusi (Globals per mod)

Setiap mod dikompilasi di Globals terpisah dengan pustaka berikut
**dibuang**: `os`, `io`, `debug`, `package`, `coroutine`, `dofile`,
`loadfile`, `load`, `loadstring`, `java`, `luajava`.

Dipertahankan: `string`, `table`, `math`, `M`, dan `print` yang
dialihkan ke `M.log`.

Akibatnya mod **tak bisa**: membaca/menulis file, memanggil proses,
mengakses jaringan/JVM, memanggil kode di luar berkasnya sendiri, atau
membuat thread. Kode mod hanya berasal dari `scripts/rules.lua` dan
`scripts/ui.lua` di paketnya.

## Determinisme & RNG

- RNG game (dadu, kartu) tetap milik engine dan bisa dipaksa (uji).
- `math.random` di dalam mod memakai RNG Lua terpisah — nondeterminisme
  jadi tanggung jawab modder. Dokumentasi modder menyatakan ini.

## api_version

- Kontrak saat ini: `api_version = 1` (dokumen 04–06).
- Bumping (`2`) dilakukan **hanya** untuk perubahan yang memutus kompat
  (hapus event, ubah semantik aksi, ubah nama fungsi).
- Mod dengan `api_version` tak dikenal → `ERROR` dengan pesan:
  `butuh api_version 1, mod meminta <n>`.
- Penambahan event/aksi baru **tanpa** bump (aditif, aman).

## Kontrak galat (ringkas)

| Kondisi | Perilaku |
|---|---|
| Handler Lua error | handler di-skip + Log Mod; handler lain jalan |
| Aksi tanpa capability | aksi di-drop + warning |
| Nama pemain tak dikenal | aksi di-drop + warning |
| `rules.lua` sintaks error | peran rules mati; peran lain aktif |
| JSON peran invalid | peran itu mati; peran lain aktif |
| Manifest/dependensi gagal | seluruh mod `ERROR` (dinonaktifkan) |
| Loop tak berhenti di handler mod | batas waktu per dispatch (mis. 2 detik) → handler dibatalkan + Log Mod `timeout` |

Tidak ada satu pun kondisi di atas yang menghentikan game.
