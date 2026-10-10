package com.monopoly.data

import com.monopoly.core.EventContext
import com.monopoly.core.GameEvent
import com.monopoly.core.ModAction
import com.monopoly.core.ModHostPort
import com.monopoly.core.ModInfo
import com.monopoly.core.ModLogEntry
import com.monopoly.core.ModStatus
import com.monopoly.core.RuleEngine
import com.monopoly.core.RuleOutcome
import com.monopoly.core.RuleRegistry
import com.monopoly.core.StateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Host mod (cetak biru dokumen 02). Menemukan, memvalidasi, menyelesaikan
 * dependensi, mengompilasi (sandbox), mendaftarkan handler, dan mengelola
 * registry aturan. Setiap kegagalan ditangani per-mod/per-peran — game tak
 * pernah berhenti karena mod.
 */
class ModHost(
    private val modsDir: File = File("mods"),
    private val stateFile: File = File(modsDir, "state.json")
) : ModHostPort {

    @Serializable
    data class ModOverride(val enabled: Boolean? = null, val priority: Int? = null)

    @Serializable
    data class ModStateFile(val overrides: Map<String, ModOverride> = emptyMap())

    private val _mods = MutableStateFlow<List<ModInfo>>(emptyList())
    override val mods: StateFlow<List<ModInfo>> = _mods

    private val _log = MutableStateFlow<List<ModLogEntry>>(emptyList())
    override val log: StateFlow<List<ModLogEntry>> = _log

    private val bus = ModEventBus()

    @Volatile
    private var _registry: RuleRegistry = RuleRegistry()

    /** Nilai aturan aktif (default ← basis ← mod, prioritas menang). */
    val registry: RuleRegistry get() = _registry

    private val pendingActions = mutableListOf<ModAction>()
    private var provider: StateProvider? = null

    @Volatile
    internal var sourceSummary = "basis"

    init { load() }

    fun attachStateProvider(p: StateProvider?) {
        provider = p
    }

    fun drainActions(): List<ModAction> = synchronized(pendingActions) {
        val copy = pendingActions.toList()
        pendingActions.clear()
        copy
    }

    override fun reload(): Boolean {
        load()
        return true
    }

    override fun setEnabled(id: String, enabled: Boolean) {
        writeOverride(id) { it.copy(enabled = enabled) }
        load()
    }

    override fun setPriority(id: String, priority: Int) {
        writeOverride(id) { it.copy(priority = priority.coerceIn(0, 999)) }
        load()
    }

    fun dispatch(event: GameEvent, ctx: EventContext): RuleOutcome =
        bus.dispatch(event, ctx) { modId, msg -> log(modId, "ERROR", msg) }

    // ====================================================== load lifecycle

    private fun load() {
        bus.clear()
        synchronized(pendingActions) { pendingActions.clear() }
        _registry = RuleRegistry()
        sourceSummary = "basis"
        val overrides = readOverrides()

        loadBase()

        // ---- discover + validate
        val infos = mutableListOf<ModInfo>()
        val valid = mutableListOf<ModManifest>()
        val dir = modsDir
        if (dir.isDirectory) {
            dir.listFiles()?.filter { it.isDirectory && it.name != "state.json" }
                ?.sortedBy { it.name }
                ?.forEach { d ->
                    val mf = File(d, "mod.json")
                    if (!mf.isFile) return@forEach // folder tanpa manifest: diabaikan
                    val text = runCatching { mf.readText() }.getOrNull()
                    if (text == null) {
                        infos.add(errInfo(d.name, "mod.json tak bisa dibaca"))
                        return@forEach
                    }
                    unknownManifestKeys(text).forEach { k ->
                        log(d.name, "WARN", "field tak dikenal diabaikan: $k")
                    }
                    val parsed = parseManifest(text)
                    val m = parsed.getOrNull()
                    if (m == null) {
                        infos.add(errInfo(d.name, parsed.exceptionOrNull()?.message ?: "mod.json tidak valid"))
                        return@forEach
                    }
                    when (val problem = validate(m, d.name)) {
                        null -> {
                            val caps = m.capabilities.filter { it in KNOWN_CAPABILITIES }
                            m.capabilities.filterNot { it in KNOWN_CAPABILITIES }
                                .forEach { log(m.id, "WARN", "capability tak dikenal diabaikan: $it") }
                            val roles = m.roles.filter { it in KNOWN_ROLES }
                            m.roles.filterNot { it in KNOWN_ROLES }
                                .forEach { log(m.id, "WARN", "peran tak dikenal diabaikan: $it") }
                            valid.add(m.copy(capabilities = caps, roles = roles))
                        }
                        else -> infos.add(errInfo(m.id, problem))
                    }
                }
        }

        // ---- status: enabled (state.json > manifest), lalu depends/conflicts
        val statuses = mutableMapOf<String, ModStatus>()
        val effective = mutableMapOf<String, ModManifest>()
        for (m in valid) {
            val ov = overrides[m.id]
            val en = ov?.enabled ?: m.enabled
            val prio = ov?.priority ?: m.priority
            val eff = m.copy(enabled = en, priority = prio)
            effective[m.id] = eff
            statuses[m.id] = if (en) ModStatus.ACTIVE else ModStatus.DISABLED
        }

        // siklus dependensi: mod yang (transitif) bergantung pada dirinya
        for ((id, _) in effective) {
            if (statuses[id] == ModStatus.ACTIVE && dependsOn(id, id, effective)) {
                statuses[id] = ModStatus.ERROR
                log(id, "ERROR", "dependensi siklik")
            }
        }

        // depends: gagal bergantung pada status dependensi (fixpoint)
        var changed = true
        var guard = effective.size + 1
        while (changed && guard-- > 0) {
            changed = false
            for ((id, m) in effective) {
                if (statuses[id] != ModStatus.ACTIVE) continue
                for (dep in m.depends) {
                    val (depId, range) = splitDep(dep)
                    val target = effective[depId]
                    if (target == null) {
                        statuses[id] = ModStatus.ERROR
                        log(id, "ERROR", "butuh $depId yang tidak terpasang")
                        changed = true
                        break
                    }
                    if (statuses[depId] == ModStatus.ERROR || statuses[depId] == ModStatus.DISABLED) {
                        statuses[id] = ModStatus.ERROR
                        log(id, "ERROR", "butuh $depId aktif (status: ${statuses[depId]})")
                        changed = true
                        break
                    }
                    if (!satisfies(target.version, range)) {
                        statuses[id] = ModStatus.ERROR
                        log(id, "ERROR", "butuh $depId@$range, ditemukan ${target.version}")
                        changed = true
                        break
                    }
                }
            }
        }

        // conflicts: keduanya ERROR
        for ((id, m) in effective) {
            if (statuses[id] != ModStatus.ACTIVE) continue
            val clash = m.conflicts.firstOrNull { statuses[it] == ModStatus.ACTIVE }
            if (clash != null) {
                statuses[id] = ModStatus.ERROR
                statuses[clash] = ModStatus.ERROR
                log(id, "ERROR", "konflik dengan $clash")
                log(clash, "ERROR", "konflik dengan $id")
            }
        }

        // ---- compile & register (basis sudah; mods urut prioritas NAIK agar tertinggi menang)
        val active = effective.values
            .filter { statuses[it.id] == ModStatus.ACTIVE }
            .sortedBy { it.priority }
        var modCount = 0
        for (m in active) {
            val ok = compileMod(m, dir(m.id))
            modCount++
            if (!ok && statuses[m.id] == ModStatus.ACTIVE && "rules" in m.roles) {
                statuses[m.id] = ModStatus.ACTIVE_DEGRADED
            }
        }
        sourceSummary = if (modCount == 0) "basis" else "basis + $modCount mod"

        // ---- hasilkan ModInfo
        for ((id, m) in effective) {
            val st = statuses[id] ?: ModStatus.ERROR
            val degraded = st == ModStatus.ACTIVE_DEGRADED
            val roles = if (m.roles.isEmpty()) inferRoles(dir(m.id)) else m.roles
            infos.add(
                ModInfo(
                    id = id, name = m.name, version = m.version, author = m.author,
                    status = st, priority = m.priority, enabled = m.enabled,
                    roles = roles.toSet(),
                    capabilities = m.capabilities.toSet(),
                    depends = m.depends, conflicts = m.conflicts,
                    error = when {
                        st == ModStatus.ERROR -> _log.value.lastOrNull { it.modId == id && it.level == "ERROR" }?.message
                        degraded -> "rules.lua gagal di-compile; peran lain tetap aktif"
                        else -> null
                    }
                )
            )
        }
        _mods.value = infos.sortedWith(compareByDescending<ModInfo> { it.priority }.thenBy { it.id })
    }

    // ============================================================ helpers

    private fun validate(m: ModManifest, folderName: String): String? = when {
        m.id != folderName -> "id \"${m.id}\" tidak cocok folder \"$folderName\""
        m.api_version != 1 -> "butuh api_version 1, mod meminta ${m.api_version}"
        SemVer.parse(m.version) == null -> "version \"${m.version}\" bukan semver"
        else -> null
    }

    private fun splitDep(dep: String): Pair<String, String> {
        val at = dep.indexOf('@')
        return if (at < 0) dep to "" else dep.substring(0, at) to dep.substring(at + 1)
    }

    /** Apakah [from] (transitif) bergantung pada [target]? Untuk deteksi siklus. */
    private fun dependsOn(from: String, target: String, mods: Map<String, ModManifest>): Boolean {
        val seen = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        mods[from]?.depends?.forEach { stack.addLast(splitDep(it).first) }
        while (stack.isNotEmpty()) {
            val cur = stack.removeFirst()
            if (cur == target) return true
            if (cur in seen) continue
            seen.add(cur)
            mods[cur]?.depends?.forEach { stack.addLast(splitDep(it).first) }
        }
        return false
    }

    private fun errInfo(id: String, error: String): ModInfo = ModInfo(
        id = id, name = id, version = "-", author = "-",
        status = ModStatus.ERROR, priority = -1, enabled = false,
        roles = emptySet(), capabilities = emptySet(),
        depends = emptyList(), conflicts = emptyList(), error = error
    )

    private fun dir(id: String): File = File(modsDir, id)

    private fun inferRoles(d: File): List<String> = buildList {
        if (File(d, "scripts/rules.lua").isFile) add("rules")
        if (File(d, "scripts/ui.lua").isFile) add("ui")
        if (File(d, "content/board.json").isFile) add("board")
        if (File(d, "content/cards.json").isFile) add("cards")
        if (File(d, "content/texts.json").isFile) add("texts")
        if (File(d, "theme/theme.json").isFile) add("theme")
        if (File(d, "assets").isDirectory) add("assets")
    }

    // ------------------------------------------------------------ basis

    private fun loadBase(): Boolean {
        val stream = javaClass.classLoader?.getResourceAsStream("rules.lua") ?: return false
        val code = stream.bufferedReader().use { it.readText() }
        val rt = LuaRuntime(
            modId = "inti",
            capabilities = KNOWN_CAPABILITIES,
            onLog = { lvl, msg -> log("inti", lvl, msg) },
            onSetRule = { k, v ->
                if (RuleRegistry.KEYS.contains(k)) _registry = _registry.set(k, v)
                else log("inti", "WARN", "kunci aturan tak dikenal: $k")
            },
            onGetRule = { k -> _registry.get(k) },
            onAction = { /* basis tak memakai aksi imperatif */ },
            stateProvider = { provider },
            tileProvider = { provider?.tiles() ?: emptyList() }
        )
        if (!rt.compile(code)) return false
        rt.handlers.forEach { h ->
            // Basis prioritas 0; handler eksplisit menimpa.
            val prio = if (h.priority < 0) 0 else h.priority
            bus.register("inti", h.event, prio, h.fn)
        }
        return true
    }

    private fun compileMod(m: ModManifest, d: File): Boolean {
        val script = File(d, "scripts/rules.lua")
        if (!script.isFile) return true // mod data-only: sah
        val code = runCatching { script.readText() }.getOrNull() ?: run {
            log(m.id, "ERROR", "scripts/rules.lua tak bisa dibaca")
            return false
        }
        val rt = LuaRuntime(
            modId = m.id,
            capabilities = m.capabilities.toSet(),
            onLog = { lvl, msg -> log(m.id, lvl, msg) },
            onSetRule = { k, v ->
                if (RuleRegistry.KEYS.contains(k)) _registry = _registry.set(k, v)
                else log(m.id, "WARN", "kunci aturan tak dikenal: $k")
            },
            onGetRule = { k -> _registry.get(k) },
            onAction = { pendingActions.add(it) },
            stateProvider = { provider },
            tileProvider = { provider?.tiles() ?: emptyList() }
        )
        val ok = rt.compile(code)
        if (ok) {
            rt.handlers.forEach { h ->
                // Handler tanpa prioritas eksplisit mewarisi prioritas mod.
                val prio = if (h.priority < 0) m.priority else h.priority
                bus.register(m.id, h.event, prio, h.fn)
            }
        }
        return ok
    }

    // ------------------------------------------------------------ state.json

    private val stateJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private fun readOverrides(): Map<String, ModOverride> {
        if (!stateFile.isFile) return emptyMap()
        return runCatching { stateJson.decodeFromString<ModStateFile>(stateFile.readText()).overrides }
            .getOrElse { emptyMap() }
    }

    private fun writeOverride(id: String, transform: (ModOverride) -> ModOverride) {
        val current = readOverrides().toMutableMap()
        current[id] = transform(current[id] ?: ModOverride())
        runCatching {
            if (!modsDir.isDirectory) modsDir.mkdirs()
            stateFile.writeText(stateJson.encodeToString(ModStateFile.serializer(), ModStateFile(current)))
        }.onFailure { log(id, "WARN", "gagal menulis state.json: ${it.message}") }
    }

    private fun log(modId: String, level: String, message: String) {
        _log.update { (it + ModLogEntry(modId, level, message)).takeLast(100) }
    }
}

/** Pembungkus RuleEngine di atas ModHost (dipakai engine & factory). */
class LuaRuleEngine(internal val host: ModHost) : RuleEngine {
    override val registry: RuleRegistry get() = host.registry
    override suspend fun dispatch(event: GameEvent, ctx: EventContext): RuleOutcome = host.dispatch(event, ctx)
    override fun attachStateProvider(provider: StateProvider?) = host.attachStateProvider(provider)
    override fun drainActions(): List<ModAction> = host.drainActions()
    override val sourceName: String get() = host.sourceSummary
    override fun reload(): Boolean = host.reload()
}
