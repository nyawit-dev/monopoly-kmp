# 03 — Manifest (mod.json)

## Skema lengkap

```json
{
  "id": "papan-pirate",
  "name": "Papan Pirate",
  "version": "1.2.0",
  "author": "kamu",
  "api_version": 1,
  "capabilities": ["read_state", "modify_money", "move_players", "ui_message"],
  "depends": ["ekonomi-inti@^1.0"],
  "conflicts": ["papan-standar"],
  "priority": 200,
  "enabled": true,
  "roles": ["rules", "board", "theme"],
  "description": "Tema bajak laut: papan, warna, dan sprite kustom."
}
```

## Spesifikasi field

| Field | Wajib | Tipe | Aturan |
|---|---|---|---|
| `id` | ya | string | `^[a-z0-9][a-z0-9-]*$`, unik; dipakai nama folder `mods/<id>/` |
| `name` | ya | string | 1–40 karakter, untuk UI |
| `version` | ya | string | semver `MAJOR.MINOR.PATCH` |
| `author` | tidak | string | default `"anonim"` |
| `api_version` | ya | int | harus `== 1` (dokumen 07) |
| `capabilities` | tidak | string[] | subset dari daftar capability (dokumen 04); default `[]` |
| `depends` | tidak | string[] | `"<id>@<range>"`, range semver (`^1.0`, `>=1.2 <2`) |
| `conflicts` | tidak | string[] | id mod lain; jika keduanya aktif → keduanya `ERROR` |
| `priority` | tidak | int | default `100`; basis `inti` = 0 |
| `enabled` | tidak | bool | default `true` |
| `roles` | tidak | string[] | subset `rules,board,cards,texts,theme,assets`; default = semua peran yang foldernya ada |
| `description` | tidak | string | ≤ 200 karakter |

Field tak dikenal → **warning** di Log Mod, field diabaikan (bukan error).

## Aturan validasi (urutan pemeriksaan)

1. File `mod.json` ada dan parse sebagai JSON object → else `ERROR`:
   `mod.json tidak valid: <detail parser>`
2. `id` ada, cocok pola, cocok nama folder → else `ERROR`:
   `id "<id>" tidak cocok folder "<folder>"` / `id tidak valid`
3. `api_version == 1` → else `ERROR`:
   `butuh api_version 1, mod meminta <n>`
4. `version` semver valid → else `ERROR`: `version "<v>" bukan semver`
5. `capabilities` ⊆ daftar dikenal → nilai tak dikenal dibuang + warning
6. `depends`: setiap dependensi harus ada mod dengan id cocok DAN versinya
   memenuhi range DAN mod itu aktif (tidak ERROR/DISABLED) → else `ERROR`:
   `butuh <id>@<range>, ditemukan <versi>` / `butuh <id> yang tidak terpasang`
7. `conflicts`: jika mod dengan id di daftar juga aktif → **kedua** mod
   `ERROR`: `konflik dengan <id>`
8. Dependensi siklus (A→B→A) → semua yang terlibat `ERROR`: `dependensi siklik`

Catatan: dependensi hanya untuk mod **aktif**; mod DISABLED tak menghalangi.

## Keputusan terkunci

- `roles` kosong/absen → loader mengisi otomatis dari folder yang ada
  (`scripts/rules.lua` → rules, `content/board.json` → board, dst.).
- Mod boleh mendeklarasikan peran yang foldernya tak ada → warning,
  peran diabaikan.
- Duplikat `id` (dua folder, mod.json sama) → yang prioritas lebih tinggi
  menang, sisanya `ERROR`: `duplikat id "<id>"`.
