# Monopoli KMP + KorGE

Monopoli lintas platform (Desktop/JVM, Web/JS, Android, iOS) yang dibangun
dengan **Kotlin Multiplatform (KMP)** dan mesin game **KorGE**, memakai
**Clean Architecture** yang memisahkan tegas aturan game dari rendering.

## Arsitektur

```
core-domain/   Murni Kotlin. Model, enum, port (interface). Tak tergantung platform/engine.
core-data/     Repository (StateFlow) + GameEngineImpl: seluruh aturan Monopoli.
core-assets/   Parser board_config.json dari commonMain/resources (harga properti tanpa recompile).
ui-korge/      KorGE sebagai "slave": implementasi GameRendererPort, hanya gambar & animasi.
app/           Entry point Korge block + DI. Satu main untuk JVM & JS.
```

Prinsip inti:
- **KorGE tak tahu aturan.** Ia hanya menerima perintah animasi (`animateDiceRoll`,
  `animatePionMove`) dari `GameRendererPort`.
- **Aset terpisah.** Ubah harga/sewa properti cukup edit `board_config.json`.
- **Anti race.** Input dikunci lewat `GamePhase` di engine; UI mengamati `StateFlow`.

## Build & Jalankan

Butuh JDK 17+ (dikembangkan dengan JDK 21). Pakai Gradle wrapper:

```bash
# Desktop (JVM)
./gradlew :app:runJvm

# Web (JS) - buka build/bin/js
./gradlew :app:jsBrowserDevelopmentRun
```

## Modul & target

| Modul        | JVM | JS  | Android | iOS |
|--------------|-----|-----|---------|-----|
| core-domain  | ✓   | ✓   | ✓       | ✓   |
| core-data    | ✓   | ✓   | ✓       | ✓   |
| core-assets  | ✓   | ✓   | ✓       | ✓   |
| ui-korge     | ✓   | ✓   | ✓       | (via app) |
| app          | ✓   | ✓   | ✓       | ✓   |

## Kontrol

- Tombol **LEMPAR** melempar dadu dan menyelesaikan giliran.
- Dialog **BELI / LEWATI** muncul saat mendarat di properti tak bertuan.

## Lisensi

MIT. Lihat [LICENSE](LICENSE).
