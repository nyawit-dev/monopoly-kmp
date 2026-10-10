# 09 — Mod Contoh (lengkap, semua peran)

Mod ini **wajib ada di repo** (`mods/contoh-pot/`) sebagai contoh hidup
dan bahan uji. Dinonaktifkan secara default (`"enabled": false`) agar
pengalaman bawaan tak berubah.

## mods/contoh-pot/mod.json

```json
{
  "id": "contoh-pot",
  "name": "Contoh: Pot Free Parking",
  "version": "1.0.0",
  "author": "tim Monopoli",
  "api_version": 1,
  "capabilities": ["read_state", "modify_money", "ui_message"],
  "depends": [],
  "conflicts": [],
  "priority": 150,
  "enabled": false,
  "roles": ["rules", "board", "cards", "texts", "theme"],
  "description": "Contoh semua peran: pot Free Parking, kartu tambahan, tema hangat."
}
```

## scripts/rules.lua

```lua
-- Aturan: gaji START lebih kecil, Free Parking jadi pot.
M.setRule("go_salary", 150)

M.on("land", function(ctx)
  if ctx.tile.name == "FREE_PARKING" then
    M.message(ctx.player.name .. " memanen pot $500!")
    M.giveMoney(ctx.player.name, 500)
  end
end, 200)

M.on("jail_enter", function(ctx)
  if ctx.reason == "three_doubles" then
    return { message = "Tiga kembar! " .. ctx.player.name .. " ke penjara." }
  end
end)
```

## content/board.json (partial override)

```json
{
  "board": {
    "tiles": [
      { "index": 20, "name": "TAMAN POT" },
      { "index": 4, "name": "PAJAK", "price": 0 }
    ]
  }
}
```

## content/cards.json (append)

```json
{
  "cards_mode": "append",
  "chance": [
    { "text": "Andu menemukan koin kuno. Terima $100.", "amount": 100 }
  ],
  "chest": [
    { "text": "Hari jadi! Setiap pemain kirim $25.", "per_player_amount": 25 }
  ]
}
```

## content/texts.json

```json
{
  "ui": { "roll": "LEMPAR!" },
  "messages": {
    "go_salary": "{player} lewat START (+{amount})"
  }
}
```

## theme/theme.json

```json
{
  "colors": {
    "bg": "#1A120B",
    "panel": "#2B1D12",
    "gold": "#E8B04B",
    "paper": "#F7EDD8"
  }
}
```

## aset (opsional)

`assets/tokens/p1.png` — sprite pion pemain 1. Bila tak ada, pion
digambar prosedural seperti bawaan.

## Yang dibuktikan mod ini

1. `setRule` menimpa angka bawaan (go_salary 200 → 150)
2. Event + aksi imperatif (pot Free Parking)
3. Balikan tabel + pesan kustom (three doubles)
4. Partial override papan (rename tile, ubah pajak)
5. Append dek Chance & Chest
6. Override label UI & template pesan
7. Override token tema (merge per-warna)
