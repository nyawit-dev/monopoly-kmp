package com.monopoly.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Uji integrasi WiFi TCP nyata (cetak biru dokumen 12 kriteria #1):
 * host + guest di satu proses, satu ronde lengkap lewat soket.
 */
class WifiSessionTest {

    private fun snapshot(turn: Int = 0) = SnapshotDto(
        players = listOf(PlayerDto("Host", 1500, 0, false, 0, 0, emptyList(), false)),
        tiles = emptyList(),
        currentPlayerIndex = 0,
        phase = Phases.WAITING_FOR_ROLL,
        turnNumber = turn
    )

    @Test
    fun hostGuest_fullRoundOverTcp() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val hostSession = HostSession("Host")
        val server = WifiHostServer(scope)
        val port = server.start(0) // port otomatis
        assertTrue(port > 0, "server harus mendapat port")

        // Host: terima satu guest di coroutine terpisah.
        var guestTransport: WifiTransport? = null
        val receivedCmds = mutableListOf<NetMessage>()
        val acceptJob = scope.launch {
            server.acceptOne { t ->
                guestTransport = t
                // Satu kolektor per transport (Channel = satu konsumen).
                t.incoming.collect { msg ->
                    when (msg) {
                        is NetMessage.Hello -> hostSession.onHello(t, msg)
                        is NetMessage.Command -> receivedCmds.add(msg)
                        else -> Unit
                    }
                }
            }
        }

        // Guest: hubungkan + kirim Hello.
        val guestT = WifiTransport.connect("127.0.0.1", port, scope)
        val guest = GameSession("Guest", guestT)
        val collectJob = scope.launch { guestT.incoming.collect { guest.onMessage(it) } }
        guestT.send(NetMessage.Hello("Guest"))

        // Tunggu Welcome sampai ke guest.
        withTimeout(10_000) {
            while (guest.myIndex.value == null) delay(50)
        }
        assertNotNull(guest.myIndex.value)
        assertEquals(1, guest.lobby.value.size)
        assertEquals("Guest", guest.lobby.value.first().name)

        // Host kirim snapshot penuh → guest menerima.
        hostSession.broadcastSnapshot(snapshot(1))
        withTimeout(10_000) {
            while (guest.state.value == null) delay(50)
        }
        assertEquals(1500, guest.state.value!!.players[0].money)

        // Host kirim delta → guest menambal.
        hostSession.broadcastDelta(
            since = 1,
            delta = SnapshotDelta(
                turnNumber = 2,
                players = mapOf(0 to PlayerDto("Host", 1400, 1, false, 0, 0, listOf(1), false))
            )
        )
        withTimeout(10_000) {
            while (guest.state.value!!.players[0].money != 1400) delay(50)
        }
        assertEquals(1400, guest.state.value!!.players[0].money)
        assertEquals(2, guest.state.value!!.turnNumber)

        // Guest kirim Command → host terima (lewat kolektor tunggal di atas).
        guest.sendCommand(Actions.BUY, 3)
        withTimeout(10_000) {
            while (receivedCmds.isEmpty()) delay(50)
        }
        assertEquals(NetMessage.Command(Actions.BUY, 3), receivedCmds.single())

        // Bersih-bersih.
        collectJob.cancel(); acceptJob.cancel()
        guestT.close()
        server.close()
        scope.launch { delay(100) } // beri waktu close
        assertTrue(true, "ronde penuh selesai tanpa galat")
    }

    @Test
    fun wireProtocol_overRealSocket_chunks() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val server = WifiHostServer(scope)
        val port = server.start(0)
        var serverSide: WifiTransport? = null
        val acceptJob = scope.launch {
            server.acceptOne { t ->
                serverSide = t
                t.incoming.collect { }
            }
        }
        val client = WifiTransport.connect("127.0.0.1", port, scope)
        // Pesan besar (> satu paket TCP) → framing harus menyusun utuh.
        val bigMessage = NetMessage.Snapshot(
            SnapshotDto(
                players = List(6) { PlayerDto("P$it", 1000 + it, it, false, 0, 0, listOf(it), false) },
                tiles = List(40) { TileDto(it, "P${it % 6}", it % 5, false) },
                currentPlayerIndex = 2,
                phase = Phases.RESOLVING_ACTION,
                message = "pesan panjang ".repeat(50),
                turnNumber = 7
            )
        )
        val got = mutableListOf<NetMessage>()
        val collectJob = scope.launch { client.incoming.first()?.let { got.add(it) } }
        // Kirim dari server ke client.
        serverSide?.send(bigMessage)
        withTimeout(10_000) {
            while (got.isEmpty()) delay(50)
        }
        assertEquals(bigMessage, got.single(), "pesan besar tersusun utuh melewati TCP")

        collectJob.cancel(); acceptJob.cancel()
        client.close()
        server.close()
    }
}
