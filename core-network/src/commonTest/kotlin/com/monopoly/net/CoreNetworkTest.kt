package com.monopoly.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Transport palsu untuk uji: menangkap kiriman, menyuntik pesan masuk. */
class FakeTransport(override val kind: String = "Fake") : GameTransport {
    override val isConnected: Boolean = true
    private val _incoming = MutableSharedFlow<NetMessage>(extraBufferCapacity = 64)
    override val incoming = _incoming.asSharedFlow()
    val sent = mutableListOf<NetMessage>()

    override suspend fun send(msg: NetMessage) { sent.add(msg) }
    override suspend fun close() {}
    suspend fun deliver(msg: NetMessage) { _incoming.emit(msg) }
}

/** Kolektor langsung (UNCONFINED) agar subscribe + terima sinkron. */
private fun collectNow(flow: kotlinx.coroutines.flow.Flow<String>, sink: MutableList<String>): Job =
    CoroutineScope(Dispatchers.Unconfined).launch { flow.collect { sink.add(it) } }

class WireProtocolTest {

    private fun snapshot(name: String = "A", money: Int = 1500) = SnapshotDto(
        players = listOf(PlayerDto(name, money, 0, false, 0, 0, emptyList(), false)),
        tiles = listOf(TileDto(1, "B", 2, false)),
        currentPlayerIndex = 0,
        phase = Phases.WAITING_FOR_ROLL,
        turnNumber = 3
    )

    @Test
    fun roundtrip_allMessageTypes() {
        val msgs = listOf(
            NetMessage.Hello("A"),
            NetMessage.Welcome("H", 0, listOf(LobbyPlayer("A", 0))),
            NetMessage.LobbyUpdate(listOf(LobbyPlayer("A", 0), LobbyPlayer("B", 1))),
            NetMessage.StartGame(listOf("A", "B")),
            NetMessage.Command(Actions.BUY, 5),
            NetMessage.Snapshot(snapshot()),
            NetMessage.Delta(3, SnapshotDelta(players = mapOf(0 to PlayerDto("A", 1400, 3, false, 0, 0, listOf(3), false)))),
            NetMessage.Event("kartu diambil"),
            NetMessage.Reject("bukan giliranmu"),
            NetMessage.Ping("A"),
            NetMessage.Pong("B"),
            NetMessage.Bye("A")
        )
        msgs.forEach { msg ->
            val frame = WireProtocol.encode(msg)
            assertEquals(msg, WireProtocol.decode(frame.copyOfRange(4, frame.size)))
        }
    }

    @Test
    fun frameReceiver_reassemblesChunks() {
        val msg = NetMessage.Snapshot(snapshot("Panjang", 9999))
        val frame = WireProtocol.encode(msg)
        val received = mutableListOf<NetMessage>()
        val rx = WireProtocol.FrameReceiver { received.add(it) }
        // kirim per 3 byte — simulasi chunk BLE
        var i = 0
        while (i < frame.size) {
            val end = minOf(i + 3, frame.size)
            rx.feed(frame.copyOfRange(i, end))
            i = end
        }
        assertEquals(1, received.size)
        assertEquals(msg, received[0])
    }

    @Test
    fun frameReceiver_twoMessagesInOneFeed() {
        val m1 = NetMessage.Ping("A")
        val m2 = NetMessage.Pong("B")
        val received = mutableListOf<NetMessage>()
        val rx = WireProtocol.FrameReceiver { received.add(it) }
        rx.feed(WireProtocol.encode(m1) + WireProtocol.encode(m2))
        assertEquals(listOf(m1, m2), received)
    }

    @Test
    fun frameReceiver_corruptHeader_resets() {
        WireProtocol.FrameReceiver { }
            .feed(byteArrayOf(0x7F.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        // header korup (>MAX): buffer dibuang, tak crash, pesan baru tetap jalan
        val m = NetMessage.Ping("A")
        val received = mutableListOf<NetMessage>()
        val rx2 = WireProtocol.FrameReceiver { received.add(it) }
        rx2.feed(byteArrayOf(0x7F.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        rx2.feed(WireProtocol.encode(m))
        assertEquals(1, received.size)
        assertEquals(m, received.first())
    }
}

class GameSessionTest {

    private fun snapshot(turn: Int = 3) = SnapshotDto(
        players = listOf(
            PlayerDto("A", 1500, 0, false, 0, 0, emptyList(), false),
            PlayerDto("B", 1500, 0, false, 0, 0, emptyList(), false)
        ),
        tiles = List(4) { TileDto(it) },
        currentPlayerIndex = 0,
        phase = Phases.WAITING_FOR_ROLL,
        turnNumber = turn
    )

    @Test
    fun welcome_setsIndexLobbyAndConnected() = runTest {
        val t = FakeTransport()
        val s = GameSession("A", t)
        s.onMessage(NetMessage.Welcome("Host", 1, listOf(LobbyPlayer("Host", 0), LobbyPlayer("A", 1))))
        assertEquals(1, s.myIndex.value)
        assertEquals(2, s.lobby.value.size)
        assertEquals(ConnState.CONNECTED, s.connState.value)
    }

    @Test
    fun snapshot_setsState() = runTest {
        val t = FakeTransport()
        val s = GameSession("A", t)
        s.onMessage(NetMessage.Snapshot(snapshot()))
        assertEquals(2, s.state.value!!.players.size)
        assertEquals(1500, s.state.value!!.players[0].money)
    }

    @Test
    fun delta_patchesState() = runTest {
        val t = FakeTransport()
        val s = GameSession("A", t)
        s.onMessage(NetMessage.Snapshot(snapshot(3)))
        // giliran 3 → delta untuk giliran 3: A beli petak 1 (uang turun, properti naik)
        s.onMessage(
            NetMessage.Delta(
                since = 3,
                state = SnapshotDelta(
                    currentPlayerIndex = 1,
                    turnNumber = 4,
                    players = mapOf(0 to PlayerDto("A", 1400, 1, false, 0, 0, listOf(1), false)),
                    tiles = mapOf(1 to TileDto(1, "A", 0, false))
                )
            )
        )
        val st = s.state.value!!
        assertEquals(1400, st.players[0].money)
        assertEquals(listOf(1), st.players[0].properties)
        assertEquals("A", st.tiles[1].ownerName)
        assertEquals(1, st.currentPlayerIndex)
        assertEquals(4, st.turnNumber)
    }

    @Test
    fun delta_mismatchedSince_isIgnored() = runTest {
        val t = FakeTransport()
        val s = GameSession("A", t)
        s.onMessage(NetMessage.Snapshot(snapshot(3)))
        s.onMessage(
            NetMessage.Delta(
                since = 99, // tak cocok dengan turnNumber lokal (3)
                state = SnapshotDelta(players = mapOf(0 to PlayerDto("A", 100, 0, false, 0, 0, emptyList(), false)))
            )
        )
        assertEquals(1500, s.state.value!!.players[0].money, "delta dengan since tak cocok diabaikan")
    }

    @Test
    fun command_isSentToTransport() = runTest {
        val t = FakeTransport()
        val s = GameSession("A", t)
        s.sendCommand(Actions.BUY, 7)
        assertEquals(NetMessage.Command(Actions.BUY, 7), t.sent.single())
    }

    @Test
    fun eventAndReject_flowToUi() = runTest {
        val t = FakeTransport()
        val s = GameSession("A", t)
        val msgs = mutableListOf<String>()
        val rejs = mutableListOf<String>()
        val job1 = collectNow(s.messages, msgs)
        val job2 = collectNow(s.rejected, rejs)
        s.onMessage(NetMessage.Event("Kartu Peluang!"))
        s.onMessage(NetMessage.Reject("bukan giliranmu"))
        job1.cancel(); job2.cancel()
        assertEquals(listOf("Kartu Peluang!"), msgs)
        assertEquals(listOf("bukan giliranmu"), rejs)
    }

    @Test
    fun leave_sendsByeAndDisconnects() = runTest {
        val t = FakeTransport()
        val s = GameSession("A", t)
        s.leave()
        assertEquals(NetMessage.Bye("A"), t.sent.single())
        assertEquals(ConnState.DISCONNECTED, s.connState.value)
    }
}

class HostSessionTest {

    @Test
    fun hello_registersGuestAndBroadcastsLobby() = runTest {
        val t1 = FakeTransport()
        val t2 = FakeTransport()
        val host = HostSession("Host")
        host.acceptGuest(t1)
        host.acceptGuest(t2)

        assertTrue(host.onHello(t1, NetMessage.Hello("A")))
        assertTrue(host.onHello(t2, NetMessage.Hello("B")))

        assertEquals(listOf(LobbyPlayer("A", 0), LobbyPlayer("B", 1)), host.lobby.value)
        // Welcome berisi indeks masing-masing
        val w1 = t1.sent.filterIsInstance<NetMessage.Welcome>().first()
        assertEquals(0, w1.yourIndex)
        val w2 = t2.sent.filterIsInstance<NetMessage.Welcome>().first()
        assertEquals(1, w2.yourIndex)
    }

    @Test
    fun hello_duplicateNameRejected() = runTest {
        val t1 = FakeTransport()
        val t2 = FakeTransport()
        val host = HostSession("Host")
        host.acceptGuest(t1)
        host.acceptGuest(t2)
        assertTrue(host.onHello(t1, NetMessage.Hello("A")))
        assertFalse(host.onHello(t2, NetMessage.Hello("A")), "nama ganda ditolak")
    }

    @Test
    fun broadcastSnapshot_reachesAllGuests() = runTest {
        val t1 = FakeTransport()
        val t2 = FakeTransport()
        val host = HostSession("Host")
        host.acceptGuest(t1)
        host.acceptGuest(t2)
        host.onHello(t1, NetMessage.Hello("A"))
        host.onHello(t2, NetMessage.Hello("B"))
        val dto = SnapshotDto(
            players = listOf(PlayerDto("A", 1500, 0, false, 0, 0, emptyList(), false)),
            tiles = emptyList(),
            currentPlayerIndex = 0,
            phase = Phases.ROLLING,
            turnNumber = 1
        )
        host.broadcastSnapshot(dto)
        assertEquals(1, t1.sent.filterIsInstance<NetMessage.Snapshot>().size)
        assertEquals(1, t2.sent.filterIsInstance<NetMessage.Snapshot>().size)
    }

    @Test
    fun startGame_broadcastsNames() = runTest {
        val t = FakeTransport()
        val host = HostSession("Host")
        host.acceptGuest(t)
        host.onHello(t, NetMessage.Hello("A"))
        host.startGame(listOf("Host", "A"))
        val sg = t.sent.filterIsInstance<NetMessage.StartGame>().single()
        assertEquals(listOf("Host", "A"), sg.players)
    }

    @Test
    fun guestLeft_updatesLobby() = runTest {
        val t1 = FakeTransport()
        val t2 = FakeTransport()
        val host = HostSession("Host")
        host.acceptGuest(t1)
        host.acceptGuest(t2)
        host.onHello(t1, NetMessage.Hello("A"))
        host.onHello(t2, NetMessage.Hello("B"))
        assertEquals(2, host.guestCount())
        host.onGuestLeft(t2)
        assertEquals(1, host.guestCount())
        assertEquals(listOf(LobbyPlayer("A", 0)), host.lobby.value)
    }

    @Test
    fun isTurnOf_validates() {
        val host = HostSession("Host")
        assertTrue(host.isTurnOf(1, 1))
        assertFalse(host.isTurnOf(0, 1))
    }
}
