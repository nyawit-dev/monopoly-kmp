pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // KorGE artifacts
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/korge") }
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") }
    }
}

rootProject.name = "monopoly-kmp"

include(":core-domain")
include(":core-data")
include(":core-assets")
include(":ui-korge")
include(":app")
