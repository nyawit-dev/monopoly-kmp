package com.monopoly.core

/**
 * Port mesin aturan (cetak biru dokumen 04).
 *
 * - [registry] — nilai aturan numerik/boolean; engine membaca dari sini.
 * - [dispatch] — jalankan semua handler mod untuk satu [GameEvent];
 *   hasilnya [RuleOutcome] yang sudah di-merge.
 *
 * Implementasi: `StandardRuleEngine` (aturan baku, tanpa mod — dipakai JS),
 * `LuaRuleEngine` (JVM, mods/ folder lewat ModHost).
 */
interface RuleEngine {
    /** Nilai aturan aktif (default ← basis Lua ← mod, prioritas menang). */
    val registry: RuleRegistry

    /** Jalankan handler mod untuk [event] dengan [ctx]; kembalikan hasil merge. */
    suspend fun dispatch(event: GameEvent, ctx: EventContext): RuleOutcome

    /**
     * Pasang pemasok state game (M.getState/getTile). Dipanggil engine saat
     * inisialisasi; no-op di implementasi tanpa mod.
     */
    fun attachStateProvider(provider: StateProvider?)

    /**
     * Aksi imperatif (`M.giveMoney` dst.) yang mengantri sejak dispatch
     * terakhir. Engine mengambil dan menerapkannya setelah dispatch.
     */
    fun drainActions(): List<ModAction>

    /** Ringkasan sumber aturan untuk UI (mis. "basis + 2 mod"). */
    val sourceName: String

    /** Muat ulang aturan (rescan mods + compile ulang); true bila berhasil. */
    fun reload(): Boolean
}
