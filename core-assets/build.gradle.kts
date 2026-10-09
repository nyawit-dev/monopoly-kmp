plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    js(IR) { browser(); nodejs() }
    // Target native hanya di host macOS; di Linux CI dilewati (lihat core-domain).
    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        iosX64(); iosArm64(); iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":core-domain"))
            }
        }
    }
}
