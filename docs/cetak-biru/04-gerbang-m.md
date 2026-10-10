# 04 — Gerbang M (kontrak API)

Satu objek global Lua `M` disuntikkan ke setiap mod (Globals terpisah per
mod). Semua yang mod boleh lakukan ada di sini — tidak ada jalan lain.

## 1. Pendaftaran handler

```lua
M.on("<event>", function(ctx) ... end [, priority])
```

- `priority`: int, default 100. Dispatch urut **desc**; sama → urut daftar
  mod (prioritas mod, lalu nama id).
- Handler boleh tidak mendaftar apa pun (mod murni data) — sah.
- Mendaftar handler yang sama dua kali → yang terakhir menang + warning.

## 2. Event & isi ctx

| Event | Kapan diemit | ctx |
|---|---|---|
| `turn_start` | giliran pemain dimulai | `player`, `turn_number` |
| `roll` | dadu dilempar (sebelum gerak) | `player`, `dice{d1,d2,total,is_double}`, `consecutive_doubles` |
| `land` | mendarat di petak | `player`, `tile`, `dice_total` |
| `pass_start` | melewati/mendarat START | `player`, `from`, `to` |
| `card_drawn` | kartu Chance/Chest diambil | `player`, `deck`("chance"/"chest"), `card{text}` |
| `property_bought` | properti dibeli | `player`, `tile`, `price` |
| `house_built` | rumah/hotel dibangun | `player`, `tile`, `count` |
| `jail_enter` | masuk penjara | `player`, `reason`("go_to_jail_card"/"three_doubles") |
| `jail_exit` | keluar penjara | `player`, `reason`("paid_fine"/"doubles"/"forced"/"card") |
| `bankrupt` | pemain bangkrut | `player`, `creditor`(nama atau null) |
| `game_over` | game selesai | `winner`, `net_worth` |

`player` = `{ name, money, position, in_jail }` (snapshot saat emit).
`tile` = `{ index, name, type, price, color_group, owner }` (owner = nama
pemain atau null). Semua nilai **read-only**; ubah lewat aksi.

## 3. Balikan handler

`nil` = tak berpendapat, atau tabel:

```lua
return {
  money_delta = -50,   -- aditif antar handler (+100 & -50 → +50 total)
  message    = "teks", -- last-wins; tampil di bar pesan sampai giliran berganti
  go_to_jail = true,   -- sticky-OR: cukup satu handler true → penjara
  stop       = true    -- hentikan handler berprioritas lebih rendah untuk event ini
}
```

## 4. Semantik merge (dikunci)

- `money_delta`: **aditif** — semua handler dijumlahkan.
- `message`: **last-wins** — handler berprioritas tinggi terakhir menang.
- `go_to_jail`: **sticky-OR** — satu true cukup.
- `stop`: menghentikan dispatch handler sisa; aksi yang sudah terkuue
  tetap berlaku.
- Aksi imperatif (bagian 5) dieksekusi **saat dipanggil**, berurutan
  menurut dispatch. `money` aditif; `movePlayer`/`sendToJail` last-wins.
- Handler melempar error Lua → handler itu di-skip + Log Mod; handler lain
  tetap jalan.

## 5. Aksi imperatif

| Fungsi | Capability | Efek |
|---|---|---|
| `M.giveMoney(player, n)` | `modify_money` | +n (n>0) |
| `M.takeMoney(player, n)` | `modify_money` | -n |
| `M.movePlayer(player, steps)` | `move_players` | gerak relatif; melewati START → gaji |
| `M.sendToJail(player)` | `jail_control` | penjara |
| `M.releaseFromJail(player)` | `jail_control` | bebas (tak bergerak) |
| `M.grantJailCard(player)` | `jail_control` | +1 kartu bebas penjara |
| `M.drawCard(player, deck)` | `draw_cards` | paksa ambil kartu (emit `card_drawn`) |
| `M.message(text)` | `ui_message` | tulis bar pesan |
| `M.log(text)` | — (bebas) | masuk Log Mod |
| `M.getState()` | `read_state` | snapshot read-only (dokumen 05? lihat bawah) |
| `M.getTile(index)` | `read_state` | info satu petak |

`player` = nama string (bukan objek). Nama tak dikenal → warning + aksi
di-drop. Aksi tanpa capability → di-drop + warning (dokumen 07).

**`M.getState()`** mengembalikan:
```lua
{
  turn = 3,
  current_player = "A",
  players = {
    { name="A", money=1500, position=10, in_jail=true,
      properties = {1, 3} },
    ...
  }
}
```

## 6. Rule registry

Nilai aturan numerik/boolean dibaca engine dari registry:

```lua
M.setRule("go_salary", 500)        -- butuh capability: none (rules umum)
M.setRule("jail_fine", 0)
M.setRule("max_houses", 4)
M.setRule("monopoly_doubles_rent", false)

local v = M.getRule("go_salary")   -- nilai aktif sekarang
```

- Kunci dikenal: `starting_cash, go_salary, jail_fine, jail_max_turns,
  max_houses, three_doubles_to_jail, monopoly_doubles_rent,
  income_tax, luxury_tax`.
- Basis (`rules.lua` bawaan, prioritas 0) mengisi nilai baku lewat
  `M.setRule` saat load. Mod user menimpa sesuai prioritas.
- Nilai akhir = mod prioritas **tertinggi** yang menulis kunci itu;
  fallback = default Kotlin.
- Kunci tak dikenal → warning + diabaikan.
- `M.setRule` boleh dipanggil saat load (top-level) atau di event
  `turn_start` (aturan dinamis per giliran).

## 7. Contoh lengkap

```lua
-- mods/pot-free-parking/scripts/rules.lua
M.setRule("go_salary", 300)   -- gaji START lebih kecil

M.on("land", function(ctx)
  if ctx.tile.name == "FREE_PARKING" then
    M.message(ctx.player.name .. " memanen pot!")
    M.giveMoney(ctx.player.name, 500)
  end
end, 200)

M.on("jail_enter", function(ctx)
  if ctx.reason == "three_doubles" then
    return { message = "Sial! " .. ctx.player.name .. " kena tiga kembar." }
  end
end)
```
