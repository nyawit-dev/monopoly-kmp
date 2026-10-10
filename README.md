# Monopoli KMP + KorGE

Monopoli lintas platform (Desktop/JVM, Web/JS, Android, iOS) yang dibangun
dengan **Kotlin Multiplatform (KMP)** dan mesin game **KorGE**, memakai
**Clean Architecture** yang memisahkan tegas aturan game dari rendering.

## Arsitektur

```
core-domain/   Murni Kotlin. Model, enum, port (interface). Tak tergantung platform/engine.
core-data/     Repository (StateFlow) + GameEngineImpl: aturan Monopoli dieksekusi di sini,
               angka & hook aturan datang dari rules.lua (bisa diedit user).
core-assets/   Parser board_config.json dari commonMain/resources (harga properti tanpa recompile).
ui-korge/      KorGE sebagai "slave": implementasi GameRendererPort, hanya gambar & animasi.
app/           Entry point Korge block + DI. Satu main untuk JVM & JS.
```

Prinsip inti:
- **KorGE tak tahu aturan.** Ia hanya menerima perintah animasi (`animateDiceRoll`,
  `animatePionMove`) dari `GameRendererPort`.
- **Aset terpisah.** Ubah harga/sewa properti cukup edit `board_config.json`.
- **Aturan terpisah.** Ubah mekanik (modal, sewa, penjara, pajak, hook event)
  cukup edit `rules.lua` — lihat bagian "Aturan Kustom (Lua)" di bawah.
- **Anti race.** Input dikunci lewat `GamePhase` di engine; UI mengamati `StateFlow`.

## Build & Jalankan

Butuh JDK 17+ (dikembangkan dengan JDK 21). Pakai Gradle wrapper:

```bash
# Desktop (JVM)
./gradlew :app:runJvm

# Web (JS) - buka build/bin/js
./gradlew :app:jsBrowserDevelopmentRun
```

## Modul & target

| Modul        | JVM | JS  | Android | iOS |
|--------------|-----|-----|---------|-----|
| core-domain  | ✓   | ✓   | ✓       | ✓   |
| core-data    | ✓   | ✓   | ✓       | ✓   |
| core-assets  | ✓   | ✓   | ✓       | ✓   |
| ui-korge     | ✓   | ✓   | ✓       | (via app) |
| app          | ✓   | ✓   | ✓       | ✓   |

## Kontrol

- Tombol **KOCOK DADU** melempar dadu dan menyelesaikan giliran.
- Dialog **BELI / LEWATI** muncul saat mendarat di properti tak bertuan.
- Tombol **BAYAR DENDA** muncul saat giliran pemain yang dipenjara.
- Tombol **MUAT ULANG ATURAN** membaca ulang `rules.lua` setelah diedit.
- Kartu Chance/Community Chest tampil sebagai overlay; tekan OK untuk lanjut.

## Aturan Kustom (Lua)

Seluruh aturan game ada di file **`rules.lua`** (Lua), bukan di kode Kotlin.
User bisa mengubah aturan sesuka hati **tanpa menyentuh kode utama**:

1. Salin file bawaan ke salah satu lokasi (urutan prioritas):
   - `./rules.lua` — folder tempat aplikasi dijalankan
   - `~/monopoly/rules.lua` — folder rumah user
2. Edit angka/fungsi yang diinginkan, simpan.
3. Di dalam game tekan **MUAT ULANG ATURAN** (atau restart).

Yang bisa diubah (semua opsional; yang tidak diisi pakai aturan baku):

```lua
function starting_cash() return 1500 end   -- modal awal
function go_salary() return 200 end        -- gaji lewat START
function jail_fine() return 50 end         -- denda penjara
function jail_max_turns() return 3 end     -- giliran maksimal di penjara
function three_doubles_to_jail() return true end  -- 3 kembar -> penjara
function max_houses() return 5 end         -- 5 = hotel
function monopoly_doubles_rent() return true end -- grup penuh = sewa x2
function income_tax() return 200 end       -- pajak Income Tax
function luxury_tax() return 100 end       -- pajak Luxury Tax
function railroad_rent(base, owned) return base * (2 ^ (owned - 1)) end
function utility_rent(dice, owned) return dice * (owned >= 2 and 10 or 4) end
```

Hook aturan kustom (kembalikan tabel, atau nil untuk bawaan):

```lua
function on_land(ctx)      -- ctx: tile_name, player_name, player_money, ...
    if ctx.tile_name == "FREE_PARKING" then
        return { money_delta = 100, message = ctx.player_name .. " parkir dapat $100!" }
    end
end
function on_card(ctx)      -- ctx.deck = "CHANCE"/"CHEST", ctx.card_text
function on_pass_start(ctx)
function on_bankrupt(ctx)
```

Bila file `rules.lua` tidak ada atau rusak, game otomatis memakai aturan
baku (tidak pernah crash). Di platform JS, aturan baku selalu dipakai
(interpreter Lua hanya tersedia di JVM/Desktop & Android).

## Lisensi

MIT. Lihat [LICENSE](LICENSE).
