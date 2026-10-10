# 12 — Multiplayer Jaringan (WiFi + Bluetooth)

## Tujuan

Main Monopoli bersama 2–6 pemain di satu jaringan lokal (WiFi/LAN) atau
lewat Bluetooth, tanpa server internet. Semua perangkat menjalankan game
yang sama; sinkronisasi lewat **sesi host-authoritative**.

## Pilihan teknologi (hasil riset)

| Transport | Pustaka | Alasan |
|---|---|---|
| WiFi/LAN | KorIO `createTcpServer`/`createTcpClient` (sudah ada di dependensi KorGE) | Suspending, multiplatform (JVM/JS/native), tak ada dependensi baru |
| Bluetooth | **Kable** 0.45.0 (`com.juul.kable:kable-core`) | KMP, coroutines+Flow, Android/iOS/desktop; satu API untuk semua platform |
| Penemuan host | UDP broadcast (WiFi) + BLE advertising (BT) | Tak butuh mDNS/NSD yang beda tiap platform |

## Arsitektur: modul `core-network` (baru)

```
core-network/
├── commonMain/
│   ├── NetMessage.kt        — protokol (kotlinx.serialization)
│   ├── GameTransport.kt     — antarmuka transport (send/incoming/close)
│   ├── SnapshotDto.kt       — state game versi kabel (UI render dari sini)
│   ├── GameSession.kt       — sesi host/guest + state machine
│   └── WireProtocol.kt      — framing: header 4-byte panjang + payload
├── jvmMain/
│   ├── WifiTransport.kt     — TCP server/client + UDP broadcast discovery
│   └── BluetoothTransport.kt— Kable: peripheral (host) & central (guest)
└── jsMain/
    └── WebSocketTransport.kt— (fase akhir, opsional)
```

## Protokol (WireProtocol)

Setiap pesan: **4 byte big-endian panjang** + payload UTF-8 JSON.
BLE membatasi MTU (~180–512 byte) → chunk otomatis: kirim header dulu,
lalu potong payload; penerima menyusun sampai panjang terpenuhi.

## Pesan (NetMessage)

| Pesan | Arah | Isi |
|---|---|---|
| `Hello` | guest → host | nama pemain |
| `Welcome` | host → guest | nama host, indeks pemain, daftar pemain |
| `LobbyUpdate` | host → semua | daftar pemain lobby (nama, indeks) |
| `StartGame` | host → semua | seed dadu, jumlah pemain |
| `Command` | guest → host | aksi: `roll`, `buy`, `skip`, `pay_fine`, `build_house`, `sell_house`, `mortgage`, `unmortgage` |
| `Snapshot` | host → semua | `SnapshotDto` (state penuh setelah aksi) |
| `Event` | host → semua | pesan/animasi (kartu, dadu) |
| `Ping`/`Pong` | dua arah | keepalive + latensi |
| `Bye` | dua arah | keluar bersih |

## Anggaran bandwidth (RAMAH MODEM)

Aturan mateng — turn-based bukan real-time, jadi tak ada aliran data
berkala. Target: **< 10 KB per ronde penuh 4 pemain**.

| Pesan | Ukuran | Kapan |
|---|---|---|
| `Command` | ~40–80 B | sekali per aksi guest |
| `Hello`/`Welcome`/`LobbyUpdate` | < 200 B | lobby |
| `Delta` | ~200–600 B | sekali per giliran (hanya field berubah) |
| `Snapshot` penuh | ~2–3 KB | hanya: join, mulai, putus-sambung, game over |
| `Ping`/`Pong` | ~30 B | 1× per 15 detik (bukan tiap 2 detik) |

**Delta, bukan snapshot penuh tiap aksi** (riset: delta compression
hemat 70–90%). Host menyimpan snapshot terakhir **per guest**; mengirim
`Delta` berisi hanya pemain/petak yang berubah. Guest menyimpan state
terakhir dan menambal. Kirim `Snapshot` penuh bila: guest baru,
reconnect, atau `since` tak cocok.

**Quantization:** semua nilai sudah integer (uang, posisi, rumah) —
tak ada float di kabel. Nama pemain dipetakan ke indeks di delta
(`players: {idx: {…}}`).

**Tak ada polling:** TCP tak perlu keepalive ketat; `Ping` longgar
(15 s) hanya untuk deteksi putus. Timeout 45 s → status TERPUTUS.

**BLE:** chunk otomatis + delta = biasanya 1–2 chunk per giliran.

## Model sesi: host-authoritative

```
HOST                                    GUEST
├─ jalankan GameEngineImpl (lokal)       ├─ kirim Command (mis. "roll")
├─ terima Command                        ├─ tunggu Snapshot
├─ validasi (bener gilirannya?)          └─ render dari Snapshot
├─ jalankan aksi di engine               (tanpa simulasi lokal)
├─ broadcast Snapshot + Event
└─ ulang
```

**Aturan matang:**
- Dadu HANYA dikocok host (acak ada di satu tempat) → deterministik, tak
  perlu sinkronisasi seed.
- Guest tak pernah menjalankan aturan; guest = terminal cerdas.
- Setiap aksi host → satu `Snapshot` broadcast. Guest yang putus
  jaringan: tombol aksi nonaktif sampai `Pong` balik (timeout 5 detik →
  status "TERPUTUS", tombol MUAT ULANG sesi).
- Host berubah pemain aktif → `Snapshot` membawa `currentPlayer`.
- Join di tengah permainan → `Welcome` + `Snapshot` penuh (observasi).

## Penemuan host (discovery)

**WiFi:** host membuka TCP server di port 47777 + UDP broadcast
`MONOPOLI-LAN|<nama>` tiap 2 detik di port 47778. Guest mendengarkan,
balas `HALO`, host balas `TCP|<ip>|<port>`.

**Bluetooth:** host jadi BLE peripheral (Kable `Advertiser`), nama =
`MONO-<nama>`, service UUID `8f2e1c4a-...`. Guest scan (`Scanner`),
hubungkan, GATT:
- characteristic `cmd` (write) — guest → host
- characteristic `state` (notify) — host → guest
- chunking lewat framing WireProtocol.

## State kabel (SnapshotDto)

```kotlin
@Serializable data class SnapshotDto(
  val players: List<PlayerDto>,      // name, money, position, inJail, properties, isBankrupt
  val tiles: List<TileDto>,          // index, ownerName?, houses, mortgaged
  val currentPlayerIndex: Int,
  val phase: String,                 // WAITING_FOR_ROLL, ROLLING, BUY_DECISION, GAME_OVER
  val message: String?,
  val lastRoll: DiceDto?,            // d1, d2, total, isDouble
  val eventCard: String?,
  val turnNumber: Int
)
```

Engine mengisi DTO via fungsi `toSnapshot()`; UI guest render dari DTO
yang sama → **satu kode render untuk lokal & jaringan**.

## UI alur

```
Menu Utama
├── MAIN LOKAL        → GameplayScene biasa (offline)
└── MAIN JARINGAN
    ├── pilih transport: [WiFi] [Bluetooth]
    ├── [BUAT GAME]  → LobbyScene (tunggu pemain, MULAI)
    └── [GABUNG]     → ScanScene (daftar host) → tunggu Welcome
GameplayScene (mode jaringan)
├── guest: tombol aksi kirim Command, spinner saat tunggu
└── host: sama seperti lokal + indikator "HOST"
```

## Kriteria terima

1. Dua JVM di satu mesin: host + guest gabung via WiFi TCP, main 1
   ronde penuh, state sinkron.
2. Command dari guest yang bukan gilirannya ditolak host + pesan.
3. Guest putus → tombol aksi nonaktif, status TERPUTUS; sambung lagi →
   Snapshot penih.
4. BLE: kirim Command + Snapshot kecil antar dua perangkat (uji di
   perangkat nyata saat rilis; CI pakai WiFi saja).
5. Pesan >MTU terchunk dan tersusun ulang utuh.

## Urutan implementasi

1. `core-network` commonMain: NetMessage + SnapshotDto + WireProtocol +
   GameTransport + GameSession (unit-testable, tanpa platform).
2. jvmMain WifiTransport (TCP + UDP discovery).
3. Sesi + UI (LobbyScene, ScanScene, GameplayScene mode jaringan).
4. jvmMain BluetoothTransport (Kable) + build.gradle.
5. Uji dua-proses di CI lokal; contoh mod tetap jalan.
