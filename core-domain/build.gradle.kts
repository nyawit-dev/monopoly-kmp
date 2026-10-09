plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Target lintas platform untuk logika murni. Tanpa dependensi engine/platform.
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    // Target iOS/native hanya dideklarasikan di host macOS (butuh toolchain
    // native). Di Linux CI (x64/arm64) dilewati agar build tak mengunduh
    // kotlin-native-prebuilt yang tak tersedia untuk host tersebut.
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
    }
}
