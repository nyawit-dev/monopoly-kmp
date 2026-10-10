# 02 — Plugin Host (ModHost)

## Lokasi & penemuan

- Folder mod: **`<workdir>/mods/`** (satu lokasi saja; di sebelah aplikasi).
- Satu subfolder = satu kandidat mod. Folder tanpa `mod.json` yang valid →
  diabaikan + catatan log (bukan error).
- Basis: `rules.lua` bawaan di resources — diperlakukan sebagai mod
  internal `id: "inti"`, prioritas 0, selalu aktif, tak bisa dinonaktifkan.

## Lifecycle

```
        discover            validate           resolve
mods/ ───────────► manifests ───────────► dependensi ───────────┐
                                                               ▼
        register ◄──────── compile ◄──────── load data roles ◄─┘
   (handlers+rules)      (sandbox Lua)      (board/cards/texts/theme/assets)
                                                               │
                                                               ▼
                                                            ACTIVE
                                                               │
                          reload (penuh, state game dipertahankan)
```

Tahap & kegagalan per tahap:

| Tahap | Kegagalan | Akibat |
|---|---|---|
| discover | folder tanpa mod.json | diabaikan + log info |
| validate | skema/semver salah | mod `ERROR` (dinonaktifkan), pesan actionable |
| resolve | depends tak terpenuhi / conflicts aktif | mod `ERROR` (dinonaktifkan) |
| load data roles | JSON invalid/skema salah | **hanya peran itu** mati; mod tetap aktif untuk peran lain |
| compile | rules.lua sintaks error | **hanya peran rules** mati; mod tetap aktif untuk peran lain |
| register | capability tak diizinkan | aksi di-drop saat runtime + warning (dokumen 04) |

## Status mod

`ACTIVE` (semua peran jalan) · `ACTIVE_DEGRADED` (sebagian peran mati, ada
di Log Mod) · `DISABLED` (dimatikan user) · `ERROR` (gagal validate/resolve).

## Reload

- Dipicu: tombol MUAT ULANG di Mod Manager, atau `ModHost.reload()`.
- Urutan: hentikan semua handler (bus dikosongkan) → discover ulang →
  lifecycle penuh → game state **tidak** di-reset (pemain, uang, kepemilikan
  tetap; papan & tema dirender ulang dari data aktif).
- Jika board aktif berubah bentuk (tile_count beda) saat game berjalan →
  game di-reset ke menu dengan pesan ("papan berubah, mulai game baru").

## state.json (dielola Mod Manager)

```
mods/state.json   -- dibuat otomatis; boleh di-gitignore
{
  "overrides": {
    "<mod-id>": { "enabled": false, "priority": 300 }
  }
}
```

Paket mod tetap **pristin** (bisa dibagikan); preferensi user terpisah.
Prioritas efektif = `state.json.priority` bila ada, else `mod.json.priority`.

## Antarmuka (core-domain)

```kotlin
enum class ModStatus { ACTIVE, ACTIVE_DEGRADED, DISABLED, ERROR }

data class ModInfo(
    val id: String, val name: String, val version: String,
    val author: String, val status: ModStatus,
    val priority: Int, val enabled: Boolean,
    val roles: Set<String>, val capabilities: Set<String>,
    val depends: List<String>, val conflicts: List<String>,
    val error: String? = null            // pesan actionable bila ERROR/degraded
)

interface ModHostPort {
    val mods: StateFlow<List<ModInfo>>     // untuk Mod Manager UI
    val log: StateFlow<List<ModLogEntry>>  // Log Mod (timestamp, modId, level, msg)
    fun reload(): Boolean
    fun setEnabled(id: String, enabled: Boolean)
    fun setPriority(id: String, priority: Int)
}
```
