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
                // Protokol sesi jaringan (dipakai NetworkBridge).
                implementation(project(":core-network"))
            }
        }
        val jvmMain by getting {
            dependencies {
                // Mesin aturan Lua (hanya JVM; JS pakai aturan baku).
                implementation(libs.luaj)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
