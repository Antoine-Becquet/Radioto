pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") {
                useVersion("8.10.0")
            }
            // Force la version Kotlin Android
            if (requested.id.id == "org.jetbrains.kotlin.android") {
                useVersion("1.9.21")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "Radioto"
include(":app")
