pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            // Forcer l'utilisation d'AGP 8.6.0 (version compatible avec votre environnement)
            if (requested.id.id == "com.android.application") {
                useVersion("8.6.0")
            }
            // Forcer la version Kotlin Android (ajustez si nécessaire)
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
