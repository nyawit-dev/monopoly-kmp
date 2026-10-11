# 11 — Roadmap Implementasi

## Fase 1 — Inti (host, bus, gerbang, sandbox)

Kerja:
- `ModHost` (core-data): discover, validate (dokumen 03), resolve
  dependensi/konflik, `state.json`
- `LuaRuntime`: Globals per mod, sandbox (dokumen 07), timeout dispatch
- `ModEventBus`: dispatch berprioritas, `stop`, merge aksi (dokumen 04 §4)
- Gerbang `M`: `on`, `log`, `getState`, `getTile`, aksi + capability check
- `RuleRegistry` + basis `rules.lua` bawaan ditulis ulang pakai
  `M.setRule`; `GameEngineImpl` baca aturan dari registry
- `GameEngineImpl` emit event di titik yang ditentukan dokumen 04 §2
- `ModHostPort` + `reloadRules()` di `GameEnginePort` tetap jalan

Kriteria terima:
- [x] Uji: manifest invalid → `ERROR` + pesan persis dokumen 03
- [x] Uji: dependensi tak terpenangi/siklik → `ERROR`
- [x] Uji: sandbox — `os`, `io`, `load` bernilai nil di dalam mod
- [x] Uji: capability ditolak → aksi di-drop + warning, tak crash
- [x] Uji: event order (priority desc), `stop` menghentikan rantai
- [x] Uji: merge — `money_delta` aditif, `message` last-wins,
      `go_to_jail` sticky-OR
- [x] Uji: handler error → di-skip, handler lain jalan
- [x] Uji: `setRule` override + fallback default Kotlin
- [x] Regresi: test engine lama tetap hijau (aturan baku identik) —
      48/48 test `core-data:jvmTest` hijau.

## Fase 2 — Peran data + Log Mod

Kerja:
- Loader board/cards/texts/theme (dokumen 05) dengan partial override
- Engine pesan lewat kunci + template `{player}` dll.
- `ThemeProvider` di ui-korge (nilai default → token theme aktif)
- Log Mod (StateFlow) + tampilan dasar

Kriteria terima:
- [ ] Uji: partial board override (rename tile, ubah pajak)
- [ ] Uji: `cards_mode` replace vs append; dua mod replace → prioritas menang
- [ ] Uji: texts override kunci UI & pesan; placeholder terisi
- [ ] Uji: theme merge per-token; warna invalid → drop + warning
- [ ] Uji: reload dengan game berjalan → state dipertahankan;
      `tile_count` berubah → reset menu
- [ ] Uji: mod dengan rules.lua rusak → peran lain tetap aktif

## Fase 3 — Mod Manager UI

Kerja: layar MOD sesuai dokumen 08 (kartu, toggle, prioritas, detail,
badge, log), akses dari Menu.

Kriteria terima:
- [x] Toggle & prioritas menulis `state.json` + reload — ModManagerScene
  (Menu → MANAJER MOD): daftar mod, badge 4 status, toggle, reload, log.
- [x] Badge status benar untuk 4 status (AKTIF/SEBAGIAN/MATI/ERROR)
- [ ] Panel detail menampilkan izin/dependensi/pesan galat
- [ ] Layar kosong → panduan

## Fase 4 — Aset

Kerja: `AssetProvider` (dokumen 06), sprite token/dadu/latar, fallback
prosedural, capability `asset_override`.

Status: sebagian — aset **prosedural** sudah jadi (dadu pip, 8 bidak
vektor di `Tokens.kt`, rumah/hotel di `Houses.kt`, strip kepemilikan);
yang belum: AssetProvider + override mod.

Kriteria terima:
- [ ] Uji/visual: mod aset prioritas tinggi menang; rusak → fallback
- [x] Token per-pemain campuran mod/prosedural jalan (bidak vektor per indeks)
- [ ] (4b) audio: kunci sfx opsional

## Fase 5 — ui.lua + mod contoh + dokumentasi

Kerja: overlay UI sederhana dari mod (pesan & tombol), `mods/contoh-pot/`
lengkap (dokumen 09), salin panduan ke `mods/README.md`.

Kriteria terima:
- [x] `mods/contoh-pot/` lengkap + `mods/README.md` (panduan modder,
  diverifikasi syntax Lua dengan interpreter asli)
- [ ] Mod contoh aktif → semua 7 bukti dokumen 09 terlihat
- [x] Mod contoh nonaktif secara default; bawaan tak berubah
- [x] README repo menautkan `docs/cetak-biru/`

## Fase 6 — Multiplayer jaringan (dokumen 12)

Kerja:
- [x] `core-network` commonMain: NetMessage + SnapshotDto + WireProtocol +
  GameTransport + GameSession/HostSession (23 unit test).
- [x] WifiTransport (TCP KorIO + UDP broadcast) + BluetoothTransport
  (Kable 0.35.0, sisi guest — host BT perlu uji perangkat nyata).
- [x] Wire engine ↔ sesi: `toSnapshotDto()` + delta builder di core-data;
  GameplayScene mode jaringan (guest kirim Command, **host mengeksekusi
  Command guest di engine** lewat antrean `HostSession.commands`).
- [x] UI: Menu → BUAT SESI/GABUNG SESI, LobbyScene, ScanScene.
- [x] Siarkan delta (bukan snapshot penuh) setelah snapshot awal.
- [x] Uji dua-proses JVM: host+guest main 1 ronde via WiFi TCP.
- [ ] Uji 2 perangkat nyata (HP) lewat WiFi.

## Fase 7 — Tampilan dinamis (dokumen 13)

Kerja:
- [x] `Layout.kt` (PORTRAIT/WIDE, breakpoint 720) + `planLayout` +
  `dockedTo`; deteksi ukuran jendela per-frame di GameplayScene.
- [x] GameplayScene: susunan ponsel vs tablet (panel samping di layar
  lebar, papan diskala; panel adaptif `panelW`).
- [x] HUD `dockedTo` sudut; tombol aksi ≥48 virtual; rotasi rapi
  (tata letak diterapkan ulang saat jendela diubah ukuran).

## Urutan ketergantungan

```
Fase 1 ──► Fase 2 ──► Fase 3
   │           │
   └──► Fase 4 ┘──► Fase 5

Fase 6 (multiplayer) ── berdiri sendiri di core-network; wiring UI
Fase 7 (tampilan)    ── berdiri sendiri; sentuh ui-korge saja
```

Fase 4 bisa mulai setelah Fase 1 (tak bergantung fase 2/3).
Fase 6 & 7 paralel dengan fase 2–5.
