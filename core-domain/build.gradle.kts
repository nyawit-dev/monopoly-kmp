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
    // iosX64/iosArm64 butuh macOS untuk compile; dideklarasikan agar source
    // set commonMain tetap tunggal, build iOS dijalankan di CI macOS.
    iosX64(); iosArm64(); iosSimulatorArm64()

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
