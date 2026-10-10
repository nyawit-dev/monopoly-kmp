package com.monopoly.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Protokol pesan sesi Monopoli (cetak biru dokumen 12). Semua pesan
 * dikirim sebagai JSON satu baris dengan framing WireProtocol.
 * Host-authoritative: guest hanya mengirim [Command]; host mengirim
 * [Snapshot] setelah setiap aksi.
 */
@Serializable
sealed class NetMessage {

    // ---- lobby ----

    /** Guest → host: minta gabung. */
    @Serializable
    @SerialName("hello")
    data class Hello(val playerName: String) : NetMessage()

    /** Host → guest: diterima, dengan indeks pemain & daftar lobby. */
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        val hostName: String,
        val yourIndex: Int,
        val players: List<LobbyPlayer>
    ) : NetMessage()

    /** Host → semua: daftar pemain lobby berubah. */
    @Serializable
    @SerialName("lobby")
    data class LobbyUpdate(val players: List<LobbyPlayer>) : NetMessage()

    /** Host → semua: permainan dimulai. */
    @Serializable
    @SerialName("start")
    data class StartGame(val players: List<String>) : NetMessage()

    // ---- permainan ----

    /**
     * Guest → host: aksi pemain. [action] salah satu dari:
     * roll, buy, skip, pay_fine, build_house, sell_house,
     * mortgage, unmortgage. [tile] indeks petak bila relevan.
     */
    @Serializable
    @SerialName("command")
    data class Command(val action: String, val tile: Int? = null) : NetMessage()

    /** Host → semua: state penuh setelah aksi (join/mulai/reconnect). */
    @Serializable
    @SerialName("snapshot")
    data class Snapshot(val state: SnapshotDto) : NetMessage()

    /**
     * Host → semua: tambalan hemat bandwidth (ramah modem). Hanya
     * pemain/petak yang berubah sejak [since]; guest menambal state
     * lokalnya. Bandingkan `since` dengan state lokal — tak cocok →
     * minta Snapshot penuh.
     */
    @Serializable
    @SerialName("delta")
    data class Delta(val since: Int, val state: SnapshotDelta) : NetMessage()

    /** Host → semua: pesan/animasi (kartu diambil, dll). */
    @Serializable
    @SerialName("event")
    data class Event(val text: String) : NetMessage()

    /** Aksi ditolak host (mis. bukan giliran pemain). */
    @Serializable
    @SerialName("reject")
    data class Reject(val reason: String) : NetMessage()

    // ---- keepalive ----

    @Serializable
    @SerialName("ping")
    data class Ping(val from: String) : NetMessage()

    @Serializable
    @SerialName("pong")
    data class Pong(val from: String) : NetMessage()

    @Serializable
    @SerialName("bye")
    data class Bye(val playerName: String) : NetMessage()
}

/** Satu pemain di lobby. */
@Serializable
data class LobbyPlayer(val name: String, val index: Int)

/** Nama pengirim opsional agar UI bisa bedakan sumber pesan. */
val NetMessage.displayName: String
    get() = when (this) {
        is NetMessage.Hello -> "Hello($playerName)"
        is NetMessage.Welcome -> "Welcome(host=$hostName)"
        is NetMessage.LobbyUpdate -> "Lobby(${players.size} pemain)"
        is NetMessage.StartGame -> "StartGame(${players.size} pemain)"
        is NetMessage.Command -> "Command($action)"
        is NetMessage.Snapshot -> "Snapshot"
        is NetMessage.Delta -> "Delta(since=$since)"
        is NetMessage.Event -> "Event($text)"
        is NetMessage.Reject -> "Reject($reason)"
        is NetMessage.Ping -> "Ping($from)"
        is NetMessage.Pong -> "Pong($from)"
        is NetMessage.Bye -> "Bye($playerName)"
    }
