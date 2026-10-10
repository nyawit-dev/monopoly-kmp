package com.monopoly.ui

import com.monopoly.net.HostSession
import com.monopoly.net.NetMessage
import com.monopoly.net.openHostServer
import korlibs.korge.input.onClick
import korlibs.korge.scene.Scene
import korlibs.korge.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lobi host (cetak biru dokumen 12): tunggu guest lewat WiFi, tambah bot
 * lokal, lalu mulai permainan. Sesi + koneksi hidup di scope IO terpisah
 * agar pembacaan soket tak membekukan UI.
 */
class LobbyScene : Scene() {

    override suspend fun SContainer.sceneMain() {
        val SW = BoardLayout.SCREEN_W
        val SH = BoardLayout.SCREEN_H
        val cx = SW / 2
        solidRect(SW, SH, Theme.BG)

        // Scope IO khusus jaringan (baca soket tak boleh di thread UI).
        val netScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val hostName = "Host"
        val hostSession = HostSession(hostName)
        val port = openHostServer(hostName, netScope) { transport ->
            netScope.launch {
                transport.incoming.collect { msg ->
                    if (msg is NetMessage.Hello) hostSession.onHello(transport, msg)
                }
                hostSession.onGuestLeft(transport)
            }
        }

        text("SESI HOST", textSize = 34.0) { color = Theme.PAPER }
            .position(cx - 85, 120)
        val statusText = text("", textSize = 13.0) { color = Theme.MUTED }
        statusText.position(cx - 120, 168)
        statusText.text = if (port > 0) "Menunggu guest di port $port" else "Gagal membuka server"

        // Daftar pemain (host + guest), diperbarui dari sesi.
        val listTop = 220.0
        val rows = mutableListOf<Text>()
        launch {
            hostSession.lobby.collect { players ->
                players.forEachIndexed { i, p ->
                    val row = rows.getOrNull(i)
                    if (row != null) row.text = "${i + 1}. ${p.name}"
                    else {
                        val t = text("${i + 1}. ${p.name}", textSize = 16.0) { color = Theme.PAPER }
                        t.position(cx - 100, listTop + i * 32)
                        rows.add(t)
                    }
                }
            }
        }

        // Bot lokal (dipilih host, masuk daftar saat mulai).
        var botCount = 0
        val botsText = text("Bot: 0", textSize = 14.0) { color = Theme.MUTED }
        botsText.position(24, 560)

        styledButton(
            scope = stage!!.views,
            label = "TAMBAH BOT",
            width = SW - 48,
            height = 52.0,
            bg = Theme.PANEL_LIGHT,
            fg = Theme.PAPER,
            border = Theme.LINE,
            fontSize = 16.0,
            corner = 12.0
        ) {
            botCount++
            botsText.text = "Bot: $botCount"
        }.position(24, 600)

        styledButton(
            scope = stage!!.views,
            label = "MULAI",
            width = SW - 48,
            height = 64.0,
            bg = Theme.GOLD,
            fg = Theme.BG,
            fontSize = 22.0,
            corner = 14.0
        ) {
            val guestNames = hostSession.lobby.value.map { it.name }
            val names = guestNames + (1..botCount).map { "Bot $it" }
            if (names.size >= 2) {
                sceneContainer.changeTo {
                    GameplayScene(playerNames = names, hostSession = hostSession)
                }
            } else {
                statusText.text = "Butuh minimal 2 pemain (guest atau bot)"
            }
        }.position(24, 672)

        val hint = text("Guest: pilih GABUNG SESI di menu utama", textSize = 12.0) {
            color = Theme.MUTED
        }
        hint.position(cx - 140, 760)
    }
}
