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
- [ ] Uji: manifest invalid → `ERROR` + pesan persis dokumen 03
- [ ] Uji: dependensi tak terpenangi/siklik → `ERROR`
- [ ] Uji: sandbox — `os`, `io`, `load` bernilai nil di dalam mod
- [ ] Uji: capability ditolak → aksi di-drop + warning, tak crash
- [ ] Uji: event order (priority desc), `stop` menghentikan rantai
- [ ] Uji: merge — `money_delta` aditif, `message` last-wins,
      `go_to_jail` sticky-OR
- [ ] Uji: handler error → di-skip, handler lain jalan
- [ ] Uji: `setRule` override + fallback default Kotlin
- [ ] Regresi: 26 test engine lama tetap hijau (aturan baku identik)

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
- [ ] Toggle & prioritas menulis `state.json` + reload
- [ ] Badge status benar untuk 4 status
- [ ] Panel detail menampilkan izin/dependensi/pesan galat
- [ ] Layar kosong → panduan

## Fase 4 — Aset

Kerja: `AssetProvider` (dokumen 06), sprite token/dadu/latar, fallback
prosedural, capability `asset_override`.

Kriteria terima:
- [ ] Uji/visual: mod aset prioritas tinggi menang; rusak → fallback
- [ ] Token per-pemain campuran mod/prosedural jalan
- [ ] (4b) audio: kunci sfx opsional

## Fase 5 — ui.lua + mod contoh + dokumentasi

Kerja: overlay UI sederhana dari mod (pesan & tombol), `mods/contoh-pot/`
lengkap (dokumen 09), salin panduan ke `mods/README.md`.

Kriteria terima:
- [ ] Mod contoh aktif → semua 7 bukti dokumen 09 terlihat
- [ ] `mods/contoh-pot/` nonaktif secara default; bawaan tak berubah
- [ ] README repo menautkan `docs/cetak-biru/`

## Fase 6 — Multiplayer jaringan (dokumen 12)

Kerja:
- [x] `core-network` commonMain: NetMessage + SnapshotDto + WireProtocol +
  GameTransport + GameSession/HostSession (23 unit test).
- [x] WifiTransport (TCP KorIO + UDP broadcast) + BluetoothTransport
  (Kable 0.35.0, sisi guest — host BT perlu uji perangkat nyata).
- [ ] Wire engine ↔ sesi: `toSnapshotDto()` + delta builder di core-data;
  GameplayScene mode jaringan (guest kirim Command, host jalankan engine).
- [ ] UI: Menu → MAIN JARINGAN, LobbyScene, ScanScene, indikator host.
- [ ] Uji dua-proses JVM: host+guest main 1 ronde via WiFi TCP.

## Fase 7 — Tampilan dinamis (dokumen 13)

Kerja:
- [ ] `Layout.kt` (PORTRAIT/WIDE) + `detectLayout` + ResizeComponent.
- [ ] GameplayScene: susunan ponsel vs tablet (panel samping di layar lebar).
- [ ] HUD `dockedTo` sudut; tombol aksi ≥48 virtual; rotasi rapi.

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
