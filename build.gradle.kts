plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.korge) apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // KorGE 6.x + library korlibs diterbitkan ke Maven Central; tak perlu
        // repo JetBrains (yang mengembalikan 503 untuk korge-reload-agent).
    }
}
