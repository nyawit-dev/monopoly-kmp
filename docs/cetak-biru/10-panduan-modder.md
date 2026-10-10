# 10 — Panduan Modder

Dokumen ini yang dilihat pembuat mod. Bahasa Indonesia, lugas, contoh
banyak. (Akan disalin ke `mods/README.md` saat implementasi fase 5.)

## Membuat mod pertama Anda

1. Buat folder `mods/<id-mod>/` di sebelah aplikasi.
2. Buat `mod.json` (wajib `id`, `name`, `version`, `api_version: 1`).
3. Tambahkan apa pun dari peran berikut — semuanya opsional.

```
mods/<id>/
├── mod.json          -- kontrak (wajib)
├── scripts/rules.lua -- aturan & event
├── scripts/ui.lua    -- overlay UI (fase 5)
├── content/board.json    -- ubah papan (per petak)
├── content/cards.json    -- dek Chance/Chest
├── content/texts.json    -- terjemahan & label
├── theme/theme.json      -- warna & tipografi
└── assets/               -- sprite token/dadu/latar
```

4. Buka **Menu → MOD** → aktifkan mod Anda → **MUAT ULANG**.
5. Salah? **Menu → MOD → LOG MOD** menunjukkan penyebabnya.

## Aturan emas

- Mulai dari contoh: salin `mods/contoh-pot/` lalu ubah.
- `api_version` selalu `1` (versi kontrak saat ini).
- Minta **hanya izin yang Anda pakai** — pemain melihatnya.
- Mod rusak tak merusak game lain: perbaiki satu per satu sambil lihat
  Log Mod.

## Gerbang M — ringkas

```lua
M.setRule("go_salary", 500)          -- ubah angka aturan
local v = M.getRule("jail_fine")     -- baca nilai aktif

M.on("land", function(ctx)           -- dengarkan event
  if ctx.tile.name == "FREE_PARKING" then
    M.giveMoney(ctx.player.name, 500)
    return { message = "Pot!", stop = false }
  end
end, 200)                            -- prioritas (naik = lebih dulu)
```

Event: `turn_start`, `roll`, `land`, `pass_start`, `card_drawn`,
`property_bought`, `house_built`, `jail_enter`, `jail_exit`, `bankrupt`,
`game_over`. Detail lengkap: `docs/cetak-biru/04-gerbang-m.md`.

Aksi: `giveMoney`, `takeMoney`, `movePlayer`, `sendToJail`,
`releaseFromJail`, `grantJailCard`, `drawCard`, `message`, `log`,
`getState`, `getTile` — beberapa butuh izin (lihat tabel di
`docs/cetak-biru/07-sandbox-keamanan.md`).

Angka aturan yang bisa diubah (`setRule`): `starting_cash`, `go_salary`,
`jail_fine`, `jail_max_turns`, `max_houses`, `three_doubles_to_jail`,
`monopoly_doubles_rent`, `income_tax`, `luxury_tax`.

## Yang TIDAK bisa dilakukan mod (sengaja)

- Menyentuh file, jaringan, atau proses lain.
- Menjalankan kode di luar `rules.lua`/`ui.lua` paketnya.
- Mengubah jumlah petak (tetap 40 di v1).
- Memakai `os`, `io`, `load`, `loadstring` (sudah dibuang dari sandbox).

## Resep cepat

**Sewa stasiun selalu $200**
```lua
M.on("land", function(ctx)
  if ctx.tile.type == "RAILROAD" and ctx.tile.owner ~= ctx.player.name then
    M.takeMoney(ctx.player.name, 200)
  end
end, 500)
```

**Bebas penjara gratis**
```lua
M.setRule("jail_fine", 0)
```

**Ubah nama petak 20 jadi "TAMAN"** — `content/board.json`:
```json
{ "board": { "tiles": [ { "index": 20, "name": "TAMAN" } ] } }
```

**Tema gelap-hangat** — `theme/theme.json`:
```json
{ "colors": { "bg": "#1A120B", "gold": "#E8B04B" } }
```
