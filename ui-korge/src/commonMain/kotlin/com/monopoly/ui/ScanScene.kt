package com.monopoly.ui

import com.monopoly.assets.JsonAssetLoader
import com.monopoly.net.DiscoveredDevice
import com.monopoly.net.GameSession
import com.monopoly.net.NetMessage
import com.monopoly.net.joinHost
import com.monopoly.net.scanHosts
import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Pindai & gabung sesi (cetak biru dokumen 12): daftar host dari UDP
 * broadcast, hubungkan, kirim Hello, tunggu Welcome, lalu masuk
 * GameplayScene sebagai guest (NetworkController).
 */
class ScanScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        val SW = BoardLayout.SCREEN_W
        val SH = BoardLayout.SCREEN_H
        val cx = SW / 2
        solidRect(SW, SH, Theme.BG)

        text("GABUNG SESI", textSize = 32.0) { color = Theme.PAPER }
            .position(cx - 95, 110)
        val statusText = text("Memindai host di jaringan...", textSize = 13.0) { color = Theme.MUTED }
        statusText.position(cx - 120, 158)

        val netScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val rows = mutableListOf<View>()
        val found = mutableListOf<DiscoveredDevice>()
        var connecting = false
        var stopScan: (() -> Unit)? = null

        suspend fun connectTo(device: DiscoveredDevice) {
            if (connecting) return
            connecting = true
            statusText.text = "Menghubungkan ke ${device.name}..."
            netScope.launch {
                try {
                    val transport = joinHost(device, netScope)
                    if (transport == null) {
                        statusText.text = "Gagal menghubungkan"
                        connecting = false
                        return@launch
                    }
                    val session = GameSession("Tamu", transport)
                    netScope.launch { transport.incoming.collect { session.onMessage(it) } }
                    transport.send(NetMessage.Hello("Tamu"))
                    // Tunggu host mulai permainan (StartGame/Snapshot).
                    withTimeout(300_000) {
                        while (!session.started.value) delay(200)
                    }
                    statusText.text = "Permainan dimulai!"
                    stopScan?.invoke()
                    val tiles = JsonAssetLoader().loadBoardConfig()
                    val controller = NetworkController(session, tiles)
                    sceneContainer.changeTo { GameplayScene(controller = controller) }
                } catch (e: Exception) {
                    statusText.text = "Koneksi gagal: ${e.message ?: "timeout"}"
                    connecting = false
                }
            }
        }

        stopScan = scanHosts(netScope) { device ->
            if (found.none { it.id == device.id }) {
                found.add(device)
                launch {
                    val row = container {
                        solidRect(SW - 48, 56.0, Theme.PANEL_LIGHT)
                        text(device.name, textSize = 16.0) { color = Theme.PAPER }
                            .position(16, 10)
                        text(device.kind, textSize = 11.0) { color = Theme.MUTED }
                            .position(16, 32)
                    }
                    row.position(24, 210 + rows.size * 64)
                    rows.add(row)
                    // Tombol hubungkan di dalam baris.
                    val btn = styledButton(
                        scope = stage!!.views,
                        label = "HUBUNGKAN",
                        width = 130.0,
                        height = 44.0,
                        bg = Theme.GOLD,
                        fg = Theme.BG,
                        fontSize = 13.0,
                        corner = 10.0
                    ) { netScope.launch { connectTo(device) } }
                    row.addChild(btn)
                    btn.position(SW - 48 - 146, 6)
                }
            }
        }

        // Tombol kembali.
        styledButton(
            scope = stage!!.views,
            label = "KEMBALI",
            width = SW - 48,
            height = 52.0,
            bg = Theme.PANEL_LIGHT,
            fg = Theme.PAPER,
            border = Theme.LINE,
            fontSize = 16.0,
            corner = 12.0
        ) {
            stopScan?.invoke()
            sceneContainer.changeTo { MenuScene() }
        }.position(24, SH - 100)
    }
}
