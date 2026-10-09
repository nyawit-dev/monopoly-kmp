plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.korge)
}

kotlin {
    jvm()
    js(IR) { browser() }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core-domain"))
                implementation(project(":core-data"))
                implementation(project(":core-assets"))
            }
        }
    }
}

korge {
    targetJvm()
    targetJs()
}
