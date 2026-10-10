# 01 — Landasan

## Tujuan

Memberi **gerbang modding** sehingga pengguna dapat mengubah aturan, data
papan, bahasa, tema, dan aset game **tanpa menyentuh kode Kotlin utama**.

## Prinsip desain (mengikat)

1. **Isolasi** — satu mod rusak tak boleh memengaruhi mod lain maupun game.
2. **Graceful degradation** — setiap kegagalan (JSON invalid, Lua error,
   dependensi tak terpenuhi) menghasilkan status mod `ERROR` + pesan jelas,
   **bukan crash**.
3. **Kontrak eksplisit** — manifest mendeklarasikan identitas, izin,
   dependensi, dan peran. Tak ada mod "ajaib" yang diimpor diam-diam.
4. **Satu sumber kebenaran per peran** — board/cards/texts/theme punya tepat
   satu nilai aktif; konflik diselesaikan prioritas + dicatat di Log Mod.
5. **Aditif untuk perilaku** — semua mod rules boleh mendengar event yang
   sama; urutan prioritas, `stop` membatalkan sisa rantai.
6. **Determinisme inti** — RNG game tetap milik engine; mod yang mau acak
   memakai RNG-nya sendiri dan bertanggung jawab atas nondeterminisme-nya.

## Arsitektur

```
┌─ app ──────────────── wiring
├─ ui-korge ─────────── ADAPTER: scenes + Mod Manager + renderer aset
├─ core-data ────────── IMPLEMENTASI:
│   ├─ ModHost         (discover/validate/resolve/load/compile/register)
│   ├─ ModEventBus     (dispatch berprioritas, stop, merge aksi)
│   ├─ LuaRuntime      (sandbox, satu Globals per mod)
│   ├─ RuleRegistry    (nilai aturan: default Kotlin ← base Lua ← mod)
│   └─ GameEngineImpl  (orchestrator; emit event via bus)
├─ core-assets ─────── data bawaan: board_config.json, rules.lua bawaan
└─ core-domain ─────── PURE: model, enum, port (GameEnginePort, ModHostPort...)
```

## Glosarium

- **Mod** — paket folder di `mods/<id>/` dengan `mod.json`.
- **Peran (role)** — jenis kontribusi mod: `rules`, `board`, `cards`,
  `texts`, `theme`, `assets`.
- **Gerbang M** — objek global Lua yang diekspos ke setiap mod.
- **Event** — kejadian game yang bisa didengar handler mod.
- **Capability** — izin yang harus dideklarasikan mod untuk memakai aksi
  tertentu.
- **Rule registry** — peta nilai aturan (go_salary, jail_fine, ...) yang
  bisa ditulis `M.setRule`.
- **Basis (base layer)** — `rules.lua` bawaan, selalu aktif, prioritas 0.

## Non-tujuan (v1)

- Bukan bahasa skrip umum: mod hanya lewat kontrak Lua yang didefinisikan
  di dokumen 04. Tak ada akses filesystem/JVM/networking dari mod.
- Bukan sistem multiplayer/network.
- Bukan editor visual untuk mod (mod ditulis tangan sebagai teks).
- ui.lua masih terbatas: overlay pesan & tombol sederhana (fase 5).
