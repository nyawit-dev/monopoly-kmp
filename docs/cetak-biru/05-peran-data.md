# 05 — Peran Data (board / cards / texts / theme)

Semua peran data memakai skema ketat. Nilai tak diisi → default bawaan
(Kotlin/`board_config.json` bawaan). Semua override **per-field**, bukan
penggantian blunt.

## board.json — `content/board.json`

```json
{
  "board": {
    "tile_count": 40,
    "tiles": [
      {
        "index": 6,
        "name": "Jalan Kartini",
        "type": "PROPERTY",
        "color_group": "LIGHTBLUE",
        "price": 100,
        "rent_schedule": [6, 30, 90, 270, 400, 550],
        "house_price": 50,
        "mortgage_value": 50
      },
      { "index": 5, "name": "Stasiun Selatan" }
    ]
  }
}
```

- **Partial override by index**: mod cukup menyebut tile yang diubah;
  field yang tak disebut memakai nilai papan aktif. Tile tanpa `index` →
  error peran (`tile tanpa index`).
- `tile_count` harus sama dengan papan aktif (40) → else error peran:
  `tile_count 40 diharapkan, mod meminta <n>`. Penambahan/pengurangan
  petak **bukan** cakupan v1.
- `type` mengubah properti yang sudah dimiliki saat game berjalan →
  **warning** (kepemilikan tetap, sewa memakai aturan tipe baru).
- `rent_schedule` harus 6 angka naik tak negatif; `house_price`,
  `mortgage_value` ≥ 0 → else error peran.
- Beberapa mod mengubah tile yang sama → prioritas tertinggi menang,
  **per-field**: mod prioritas rendah mengisi field yang mod tinggi tak
  sentuh (merge berlapis, bukan blunt replace).

## cards.json — `content/cards.json`

```json
{
  "cards_mode": "replace",
  "chance": [
    { "text": "Bank salah kirim. Terima $200.", "amount": 200 },
    { "text": "Maju ke START.", "go_to": 0 },
    { "text": "Masuk penjara.", "go_to_jail": true },
    { "text": "Kartu bebas penjara.", "jail_free": true },
    { "text": "Bayar tiap pemain $50.", "per_player_amount": -50 },
    { "text": "Mundur 3 petak.", "back_spaces": 3 },
    { "text": "Perbaiki: $25/rumah, $100/hotel.", "per_house_amount": 25, "per_hotel_amount": 100 }
  ],
  "chest": [ ... ]
}
```

- Field kartu sama dengan model internal: `text` (wajib), `amount`,
  `go_to`, `back_spaces`, `go_to_jail`, `jail_free`, `per_player_amount`,
  `per_house_amount`, `per_hotel_amount`.
- `cards_mode`:
  - `"replace"` (default) — dek bawaan diganti dek mod ini.
  - `"append"` — kartu mod ditambahkan ke dek bawaan.
- Dua mod sama-sama `replace` → prioritas tertinggi menang + Log Mod.
  `append` dari banyak mod → semua digabung.
- `text` kosong/duplikat dalam satu dek → kartu di-drop + warning.

## texts.json — `content/texts.json`

```json
{
  "tiles": { "20": "TAMAN", "4": "PAJAK PENGHASILAN" },
  "ui": {
    "roll": "KOCOK DADU", "buy": "BELI", "skip": "LEWATI",
    "pay_fine": "BAYAR DENDA", "reload_rules": "MUAT ULANG ATURAN"
  },
  "messages": {
    "jail_enter": "{player} masuk penjara!",
    "go_salary": "{player} melewati START, terima {amount}",
    "game_over": "Permainan selesai! Pemenang: {winner}"
  }
}
```

- `tiles`: kunci = index petak (string), nilai = nama tampilan.
- `ui`: kunci label UI yang dikenal (daftar dikunci di kode; kunci tak
  dikenal → warning).
- `messages`: template dengan placeholder `{player}`, `{amount}`,
  `{tile}`, `{winner}`, `{count}`, `{deck}`, `{card}`. Placeholder tak
  dikenal dibiarkan apa adanya.
- Engine merujuk pesan lewat kunci (bukan string hardcode) — default
  Bahasa Indonesia ada di Kotlin; texts.json menimpa per kunci.
- Dua mod menimpa kunci sama → prioritas tertinggi menang.

## theme.json — `theme/theme.json`

```json
{
  "colors": {
    "bg": "#0F1420", "panel": "#1B2436", "panel_light": "#243049",
    "gold": "#D4AF37", "paper": "#F5F1E8", "accent": "#C8102E"
  },
  "tile_colors": { "BROWN": "#8B5A2B", "LIGHTBLUE": "#7EC8E3" },
  "typography": { "family": "sans-serif",
                  "scale": { "title": 32, "body": 14, "small": 11 } },
  "spacing": { "unit": 8 },
  "radius": { "card": 12, "button": 8 }
}
```

- Token sama dengan `Theme` Kotlin; token tak diisi → default.
- Warna format `#RRGGBB` atau `#RRGGBBAA`; format salah → token di-drop
  + warning (bukan error peran).
- `tile_colors`: kunci = nama grup (`BROWN`, `LIGHTBLUE`, ...); dipakai
  mewarnai petak papan & kartu properti.
- Tema mod **menggantikan** tema mod prioritas lebih rendah; merge
  per-token dengan default Kotlin sebagai dasar.

## Urutan resolusi (untuk semua peran data)

```
default bawaan (Kotlin/board_config.json)
  ← mod prioritas 0 ("inti", rules.lua bawaan untuk angka)
  ← mod prioritas naik, peran yang dideklarasikan
  ← nilai aktif
```

Konflik selalu tercatat di Log Mod: `<mod> menimpa <field> milik <mod>`.
