plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    js(IR) { browser(); nodejs() }
    if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) {
        iosX64(); iosArm64(); iosSimulatorArm64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                // Sesi WiFi (TCP KorIO + UDP broadcast).
                implementation(libs.korio.network)
                // Bluetooth LE (Kable): host advertise, guest scan.
                implementation(libs.kable)
            }
        }
    }
}
