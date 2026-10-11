# Panduan Membuat Mod Monopoli

Selamat datang di sistem mod Monopoli. Dokumen ini untuk siapa saja yang
mau mengubah cara permainan berjalan: aturan, papan, kartu, teks, tema,
atau semuanya sekaligus.

**Tidak perlu menjadi programmer untuk mulai.** Salin contoh yang sudah
ada, ubah beberapa angka, dan jalankan.

- Contoh hidup: [`contoh-pot/`](contoh-pot/)
- Kontrak teknis lengkap: [`../docs/cetak-biru/`](../docs/cetak-biru/)
  (mulai dari [10-panduan-modder.md](../docs/cetak-biru/10-panduan-modder.md))

---

## 1. Mod itu apa, dan di mana rumahnya

Mod adalah **satu folder** berisi satu file wajib (`mod.json`) dan
folder-folder isi yang opsional. Semua mod tinggal di satu tempat:

```
mods/
├── contoh-pot/          <-- contoh yang bisa langsung disalin
│   ├── mod.json         wajib: "kartu identitas" mod
│   └── scripts/
│       └── rules.lua    aturan & event game
├── papan-pirate/        <-- mod lain buatan Anda
└── state.json           daftar mod yang Anda nyalakan/matikan (otomatis)
```

Nama folder **harus sama** dengan isi field `id` di dalam `mod.json`.
Contoh: folder `papan-pirate/` wajib punya `"id": "papan-pirate"`.
Kalau tidak sama, mod langsung ditolak dengan pesan jelas di Log Mod.

### Peran (role) yang bisa diisi mod

| Folder di dalam mod | Peran | Isi |
|---|---|---|
| `scripts/rules.lua` | `rules` | Ubah angka aturan + dengar event game |
| `scripts/ui.lua` | `ui` | Overlay antarmuka |
| `content/board.json` | `board` | Ubah nama/harga petak, satu per satu |
| `content/cards.json` | `cards` | Tambah kartu Chance & Community Chest |
| `content/texts.json` | `texts` | Terjemahan dan label UI |
| `theme/theme.json` | `theme` | Warna & huruf |
| `assets/` | `assets` | Gambar pion, dadu, latar |

Semua folder isi **opsional**. Mod yang hanya mengubah satu angka tetap
mod yang sah.

---

## 2. mod.json, field demi field

Salin blok ini ke `mods/<id-mod>/mod.json` lalu isi:

```json
{
  "id": "papan-pirate",
  "name": "Papan Pirate",
  "version": "1.0.0",
  "author": "nama-anda",
  "api_version": 1,
  "capabilities": ["read_state", "modify_money", "ui_message"],
  "depends": [],
  "conflicts": [],
  "priority": 100,
  "enabled": true,
  "roles": ["rules"],
  "description": "Aturan tambahan: pot Free Parking dan denda penjara mahal."
}
```

| Field | Wajib? | Tipe | Penjelasan sederhana |
|---|---|---|---|
| `id` | **ya** | teks | Huruf kecil, angka, dan tanda `-`. Harus sama dengan nama folder. |
| `name` | **ya** | teks | Nama yang muncul di menu MOD (1 sampai 40 huruf). |
| `version` | **ya** | teks | Format `MAJOR.MINOR.PATCH`, contoh `1.0.0`. |
| `api_version` | **ya** | angka | Harus `1`. Ini versi kontrak, bukan versi mod Anda. |
| `author` | tidak | teks | Nama pembuat. Kosong berarti "anonim". |
| `capabilities` | tidak | daftar | **Izin** yang mod Anda minta. Minta secukupnya saja. |
| `depends` | tidak | daftar | Mod lain yang wajib ada, format `"id@1.0"`. |
| `conflicts` | tidak | daftar | Mod yang tidak boleh hidup bersamaan dengan mod Anda. |
| `priority` | tidak | angka | Makin besar, makin awal diproses. Mod dasar = `0`, default `100`. |
| `enabled` | tidak | true/false | Mod contoh sengaja `false` supaya tidak mengubah permainan bawaan. |
| `roles` | tidak | daftar | Peran yang dipakai. Kosongkan agar terisi otomatis dari folder. |
| `description` | tidak | teks | Penjelasan singkat, maksimal 200 huruf. |

Field yang tidak dikenali tidak membuat mod gagal: game menulis
peringatan di Log Mod lalu mengabaikannya.

### Izin (capability) yang tersedia

Izin dipakai untuk aksi yang mengubah permainan. Tanpa izin yang sesuai,
aksi diabaikan dan game menulis peringatan.

| Capability | Dipakai oleh |
|---|---|
| `read_state` | `M.getState()`, `M.getTile()` |
| `modify_money` | `M.giveMoney`, `M.takeMoney` |
| `move_players` | `M.movePlayer` |
| `jail_control` | `M.sendToJail`, `M.releaseFromJail`, `M.grantJailCard` |
| `draw_cards` | `M.drawCard` |
| `ui_message` | `M.message` |
| `ui_overlay` | Overlay UI |
| `asset_override` | Mengganti aset bawaan |

Mengubah aturan lewat `M.setRule` dan menulis log lewat `M.log` **tidak
perlu izin apa pun**.

> Aturan emas: minta **hanya izin yang benar-benar Anda pakai**. Pemain
> melihat daftar izin mod Anda di menu MOD.

---

## 3. Gerbang M, ringkas

Dalam `rules.lua` Anda memakai satu objek global bernama `M`. Tidak ada
cara lain menyentuh permainan.

### 3.1 Mengubah angka aturan

```lua
M.setRule("go_salary", 300)      -- ubah nilai aturan
local v = M.getRule("jail_fine") -- baca nilai yang sedang aktif
```

Baris di atas `M.on(...)` dijalankan sekali saat mod dimuat. Menaruh
`M.setRule` di dalam event `turn_start` juga boleh, kalau Anda mau aturan
yang berubah tiap giliran.

### 3.2 Mendengarkan event

```lua
M.on("land", function(ctx)
  -- ctx berisi snapshot pemain & petak saat event terjadi
end, 200)   -- angka terakhir opsional: prioritas handler
```

Handler boleh tidak mengembalikan apa-apa (artinya "tidak berpendapat"),
atau mengembalikan tabel:

```lua
return {
  money_delta = -50,    -- dijumlahkan dengan handler mod lain
  message    = "teks",  -- tampil di bar pesan
  go_to_jail = true,    -- satu handler true saja sudah masuk penjara
  stop       = true     -- hentikan handler berprioritas lebih rendah
}
```

### 3.3 Aksi langsung

| Fungsi | Izin perlu | Efek |
|---|---|---|
| `M.giveMoney(pemain, n)` | `modify_money` | Tambah uang |
| `M.takeMoney(pemain, n)` | `modify_money` | Kurangi uang |
| `M.movePlayer(pemain, langkah)` | `move_players` | Gerak relatif; lewat START dapat gaji |
| `M.sendToJail(pemain)` | `jail_control` | Kirim ke penjara |
| `M.releaseFromJail(pemain)` | `jail_control` | Bebas tanpa pindah |
| `M.grantJailCard(pemain)` | `jail_control` | Tambah kartu bebas penjara |
| `M.drawCard(pemain, dek)` | `draw_cards` | Paksa ambil kartu (`"chance"` / `"chest"`) |
| `M.message(teks)` | `ui_message` | Tulis bar pesan |
| `M.log(teks)` | - | Masuk Log Mod (untuk debugging) |
| `M.getState()` | `read_state` | Snapshot read-only permainan |
| `M.getTile(index)` | `read_state` | Info satu petak |

Nama pemain ditulis sebagai teks, bukan objek. Yang belum ada di
permainan akan diabaikan dengan peringatan.

---

## 4. Sembilan angka aturan yang bisa diubah

Ini daftar lengkap. Kunci lain akan ditolak dengan peringatan.

| Kunci | Bawaan | Arti |
|---|---|---|
| `starting_cash` | `1500` | Modal awal tiap pemain |
| `go_salary` | `200` | Gaji saat melewati/mendarat START |
| `jail_fine` | `50` | Denda untuk keluar penjara |
| `jail_max_turns` | `3` | Giliran maksimum di penjara |
| `max_houses` | `5` | Rumah maksimum (`5` berarti hotel) |
| `three_doubles_to_jail` | `true` | Tiga dadu kembar berturut-turut = penjara |
| `monopoly_doubles_rent` | `true` | Dadu kembar di properti lengkap = sewa dobel |
| `income_tax` | `200` | Pajak penghasilan (petak 4) |
| `luxury_tax` | `100` | Pajak mewah (petak 38) |

Nilai akhir ditentukan oleh mod berprioritas **tertinggi** yang menulis
kunci tersebut. Tidak ada yang menulis, nilai bawaan yang dipakai.

Catatan: sewa stasiun (25/50/100/200) dan utilitas (4x/10x dadu) adalah
**rumus di dalam engine**, bukan angka yang bisa diubah lewat `setRule`.
Mod yang mau mengubahnya memakai event `land` + `M.takeMoney`.

---

## 5. Sebelas event yang bisa didengar

| Event | Kapan terjadi | Isi `ctx` yang bisa dibaca |
|---|---|---|
| `turn_start` | Giliran pemain dimulai | `player`, `turn_number` |
| `roll` | Dadu dilempar (sebelum gerak) | `player`, `dice`, `consecutive_doubles` |
| `land` | Mendarat di petak | `player`, `tile`, `dice` |
| `pass_start` | Melewati/mendarat START | `player`, `from`, `to` |
| `card_drawn` | Kartu Chance/Chest diambil | `player`, `deck`, `card_text` |
| `property_bought` | Properti dibeli | `player`, `tile`, `price` |
| `house_built` | Rumah/hotel dibangun | `player`, `tile`, `house_count` |
| `jail_enter` | Masuk penjara | `player`, `reason` (`"three_doubles"` / `"go_to_jail_card"`) |
| `jail_exit` | Keluar penjara | `player`, `reason` (`"paid_fine"` / `"doubles"` / `"forced"` / `"card"`) |
| `bankrupt` | Pemain bangkrut | `player`, `creditor` |
| `game_over` | Permainan selesai | `winner`, `net_worth` |

Isi `ctx`:

- `player` = `{ name, money, position, in_jail, properties }`
- `tile` = `{ index, name, type, price, color_group, owner }`
- `dice` = `{ d1, d2, total, is_double }`

Semua nilai ini **hanya untuk dibaca**. Mengubahnya langsung tidak
menyebabkan apa-apa; perubahan harus lewat aksi `M.*` di atas.

Field yang tidak relevan untuk sebuah event bernilai `nil`. Contoh:
`ctx.tile` di event `turn_start` adalah `nil`, jadi selalu periksa dulu:

```lua
if ctx.tile ~= nil and ctx.tile.name == "FREE_PARKING" then ... end
```

---

## 6. Contoh minimal, siap salin

Buat dua file berikut di folder `mods/halo-dunia/`.

**`mods/halo-dunia/mod.json`**

```json
{
  "id": "halo-dunia",
  "name": "Halo Dunia",
  "version": "1.0.0",
  "author": "nama-anda",
  "api_version": 1,
  "capabilities": ["modify_money", "ui_message"],
  "priority": 100,
  "enabled": true,
  "roles": ["rules"],
  "description": "Mod pertama: modal lebih besar dan bonus di Free Parking."
}
```

**`mods/halo-dunia/scripts/rules.lua`**

```lua
-- Modal awal 2000 (bawaan 1500)
M.setRule("starting_cash", 2000)

-- Mendarat di Free Parking (petak 20): bonus 500
M.on("land", function(ctx)
  if ctx.tile ~= nil and ctx.tile.name == "FREE_PARKING" then
    M.message(ctx.player.name .. " dapat bonus 500!")
    M.giveMoney(ctx.player.name, 500)
  end
end)

-- Masuk penjara karena tiga kembar: pesan khusus
M.on("jail_enter", function(ctx)
  if ctx.reason == "three_doubles" then
    return { message = "Tiga kembar! " .. ctx.player.name .. " ditahan." }
  end
end)
```

Mod ini lengkap dan bisa dijalankan. Setiap tambahan yang Anda tulis di
luar itu, bandingkan dulu dengan daftar aturan, event, dan aksi di atas.

---

## 7. Menyalakan dan mematikan mod

Dua cara, keduanya menulis ke `mods/state.json` sehingga pilihan Anda
bertahan setelah permainan ditutup.

1. **Menu MOD** (cara paling mudah)
   Buka **Menu MOD**, nyalakan sakelar mod yang diinginkan, tekan
   **MUAT ULANG**. Sekalian di situ Anda bisa mengubah prioritas.

2. **Edit `mods/state.json`** (kalau lebih suka teks)

   ```json
   {
     "overrides": {
       "contoh-pot": { "enabled": false, "priority": 150 }
     }
   }
   ```

   Hapus saja berkas ini untuk kembali ke setelan bawaan tiap mod.

Nilai `enabled` di `mod.json` adalah setelan **bawaan mod**. Kalau
`state.json` sudah menyebut mod yang sama, `state.json` yang menang.

Setelan `enabled` yang sudah dicatat di `state.json` akan tetap berlaku
walaupun Anda mengubah `mod.json`; hapus berkas itu bila ingin
mengembalikan kontrol ke manifest.

---

## 8. Saat ada masalah

Urutan pemeriksaan saat mod gagal dimuat:

1. `mod.json` bukan JSON yang sah
2. `id` tidak sama dengan nama folder
3. `api_version` bukan `1`
4. `version` bukan format semver
5. `capabilities` memuat izin tak dikenal (diabaikan + peringatan)
6. `depends`: mod yang dibutuhkan belum terpasang atau versinya tidak cocok
7. `conflicts`: mod yang bentrok sama-sama aktif
8. Dependensi melingkar (A butuh B, B butuh A)

**Menu MOD -> LOG MOD** selalu menunjukkan penyebabnya. Mod yang rusak
tidak menjatuhkan permainan: peran `rules` mod itu yang mati, sisanya
tetap jalan. Perbaiki satu mod setiap kali sambil memperhatikan Log Mod.

Yang **tidak bisa** dilakukan mod, memang disengaja:

- Menyentuh berkas, jaringan, atau proses lain
- Menjalankan kode di luar `rules.lua`/`ui.lua` miliknya sendiri
- Mengubah jumlah petak (tetap 40 di versi 1)
- Memakai `os`, `io`, `load`, `loadstring` (sudah dibuang dari sandbox)

---

## 9. Bacaan lanjutan

Semua dokumen rancangan ada di [`../docs/cetak-biru/`](../docs/cetak-biru/):

| Dokumen | Isi |
|---|---|
| [01-landasan.md](../docs/cetak-biru/01-landasan.md) | Prinsip dan arsitektur |
| [03-manifest.md](../docs/cetak-biru/03-manifest.md) | Skema `mod.json` lengkap + pesan galat |
| [04-gerbang-m.md](../docs/cetak-biru/04-gerbang-m.md) | Kontrak API M: event, `ctx`, aksi, registry |
| [05-peran-data.md](../docs/cetak-biru/05-peran-data.md) | Format `board.json`, `cards.json`, `texts.json` |
| [06-aset.md](../docs/cetak-biru/06-aset.md) | Mengganti gambar pion, dadu, latar |
| [07-sandbox-keamanan.md](../docs/cetak-biru/07-sandbox-keamanan.md) | Model izin dan batas eksekusi |
| [08-mod-manager-ui.md](../docs/cetak-biru/08-mod-manager-ui.md) | Tampilan menu MOD |
| [09-mod-contoh.md](../docs/cetak-biru/09-mod-contoh.md) | Spesifikasi mod contoh semua peran |
| [10-panduan-modder.md](../docs/cetak-biru/10-panduan-modder.md) | Panduan modder versi teknis |
