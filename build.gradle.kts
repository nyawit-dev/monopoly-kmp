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
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/korge") }
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
    }
}
