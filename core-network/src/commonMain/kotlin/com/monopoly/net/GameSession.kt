package com.monopoly.net

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Sesi sisi GUEST (cetak biru dokumen 12). Guest tak pernah menjalankan
 * aturan: ia mengirim [Command], menerima [Snapshot]/[Delta], dan
 * menambal state lokalnya. UI merender dari [state].
 */
class GameSession(
    private val myName: String,
    private val transport: GameTransport
) {
    private val _connState = MutableStateFlow(ConnState.IDLE)
    val connState: StateFlow<ConnState> = _connState.asStateFlow()

    private val _lobby = MutableStateFlow<List<LobbyPlayer>>(emptyList())
    val lobby: StateFlow<List<LobbyPlayer>> = _lobby.asStateFlow()

    private val _myIndex = MutableStateFlow<Int?>(null)
    val myIndex: StateFlow<Int?> = _myIndex.asStateFlow()

    private val _state = MutableStateFlow<SnapshotDto?>(null)
    val state: StateFlow<SnapshotDto?> = _state.asStateFlow()

    /** true setelah host mengirim StartGame/Snapshot (permainan berjalan). */
    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val messages = _messages.asSharedFlow()

    private val _rejected = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val rejected = _rejected.asSharedFlow()

    /** Handler pesan masuk; dipasang UI/transport. */
    suspend fun onMessage(msg: NetMessage) {
        when (msg) {
            is NetMessage.Welcome -> {
                _myIndex.value = msg.yourIndex
                _lobby.value = msg.players
                _connState.value = ConnState.CONNECTED
            }
            is NetMessage.LobbyUpdate -> _lobby.value = msg.players
            is NetMessage.StartGame -> {
                _connState.value = ConnState.CONNECTED
                _started.value = true
            }
            is NetMessage.Snapshot -> {
                _state.value = msg.state
                _connState.value = ConnState.CONNECTED
                _started.value = true
            }
            is NetMessage.Delta -> applyDelta(msg.since, msg.state)
            is NetMessage.Event -> _messages.emit(msg.text)
            is NetMessage.Reject -> _rejected.emit(msg.reason)
            else -> Unit // ping/pong ditangani transport
        }
    }

    private fun applyDelta(since: Int, delta: SnapshotDelta) {
        val current = _state.value
        if (current == null || current.turnNumber != since) {
            // Basis tak cocok: minta snapshot penuh (hemat tetap: sekali).
            return
        }
        _state.update { s ->
            s ?: return@update s
            s.copy(
                currentPlayerIndex = delta.currentPlayerIndex ?: s.currentPlayerIndex,
                phase = delta.phase ?: s.phase,
                message = delta.message ?: s.message,
                lastRoll = delta.lastRoll ?: s.lastRoll,
                eventCard = delta.eventCard ?: s.eventCard,
                consecutiveDoubles = delta.consecutiveDoubles ?: s.consecutiveDoubles,
                turnNumber = delta.turnNumber ?: s.turnNumber,
                players = patchPlayers(s.players, delta.players),
                tiles = patchTiles(s.tiles, delta.tiles)
            )
        }
    }

    private fun patchPlayers(base: List<PlayerDto>, patch: Map<Int, PlayerDto?>): List<PlayerDto> {
        val out = base.toMutableList()
        patch.forEach { (idx, dto) ->
            if (dto == null) {
                if (idx in out.indices) out[idx] = out[idx].copy(isBankrupt = true)
            } else {
                while (out.size <= idx) out.add(dto)
                out[idx] = dto
            }
        }
        return out
    }

    private fun patchTiles(base: List<TileDto>, patch: Map<Int, TileDto>): List<TileDto> {
        val out = base.toMutableList()
        patch.forEach { (idx, dto) ->
            while (out.size <= idx) out.add(dto)
            out[idx] = dto
        }
        return out
    }

    /** Kirim aksi ke host; host yang memvalidasi giliran. */
    suspend fun sendCommand(action: String, tile: Int? = null) {
        transport.send(NetMessage.Command(action, tile))
    }

    suspend fun leave() {
        transport.send(NetMessage.Bye(myName))
        transport.close()
        _connState.value = ConnState.DISCONNECTED
    }
}

/**
 * Sesi sisi HOST (cetak biru dokumen 12). Host menjalankan engine
 * (GameEngineImpl di core-data), menerima Command dari guest,
 * memvalidasinya, lalu menyiarkan Snapshot/Delta ke semua guest.
 */
class HostSession(private val hostName: String) {

    private class Guest(
        val transport: GameTransport,
        var name: String = "?",
        var index: Int = -1
    )

    private val guests = mutableListOf<Guest>()

    private val _lobby = MutableStateFlow<List<LobbyPlayer>>(emptyList())
    val lobby: StateFlow<List<LobbyPlayer>> = _lobby.asStateFlow()

    /** Terima koneksi guest baru; panggil dari server transport. */
    suspend fun acceptGuest(transport: GameTransport) {
        val g = Guest(transport)
        guests.add(g)
        // Aliran pesan guest ditangani pemanggil (transport.incoming).
    }

    /** Daftarkan nama guest setelah Hello; balas Welcome. */
    suspend fun onHello(transport: GameTransport, hello: NetMessage.Hello): Boolean {
        // Auto-accept: guest belum terdaftar (koneksi baru langsung Hello).
        var g = guests.firstOrNull { it.transport === transport }
        if (g == null) {
            g = Guest(transport)
            guests.add(g)
        }
        if (guests.any { it.name == hello.playerName && it !== g }) return false // nama ganda
        g.name = hello.playerName
        g.index = guests.indexOf(g)
        transport.send(
            NetMessage.Welcome(hostName, g.index, _lobby.value + LobbyPlayer(g.name, g.index))
        )
        broadcastLobby()
        return true
    }

    /** Guest putus: keluarkan dari lobby. */
    suspend fun onGuestLeft(transport: GameTransport) {
        guests.removeAll { it.transport === transport }
        broadcastLobby()
    }

    private suspend fun broadcastLobby() {
        val players = guests.map { LobbyPlayer(it.name, it.index) }
        _lobby.value = players
        broadcast(NetMessage.LobbyUpdate(players))
    }

    /** Mulai permainan: semua nama (host + guest) dikirim. */
    suspend fun startGame(playerNames: List<String>) {
        broadcast(NetMessage.StartGame(playerNames))
    }

    /** Siarkan snapshot PENUH (join/mulai/reconnect). */
    suspend fun broadcastSnapshot(dto: SnapshotDto) {
        broadcast(NetMessage.Snapshot(dto))
    }

    /** Siarkan delta hemat bandwidth. [since] = turnNumber state lama. */
    suspend fun broadcastDelta(since: Int, delta: SnapshotDelta) {
        broadcast(NetMessage.Delta(since, delta))
    }

    suspend fun broadcastEvent(text: String) = broadcast(NetMessage.Event(text))
    suspend fun rejectAll(reason: String) = broadcast(NetMessage.Reject(reason))

    private suspend fun broadcast(msg: NetMessage) {
        guests.forEach { g -> runCatching { g.transport.send(msg) } }
    }

    /** Validasi: benar [playerIndex] yang sedang giliran? */
    fun isTurnOf(playerIndex: Int, currentPlayerIndex: Int): Boolean =
        playerIndex == currentPlayerIndex

    fun guestCount(): Int = guests.size
    fun guestNames(): List<String> = guests.map { it.name }
}
