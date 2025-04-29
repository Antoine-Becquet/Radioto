plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "1.9.21"
}

repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

android {
    namespace = "com.antoinebecquet.radioto"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.antoinebecquet.radioto"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Mise à jour du Compose Compiler pour compatibilité avec Kotlin 1.9.21
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.6" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    // Bibliothèques Android standards
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // ExoPlayer pour la lecture audio
    implementation("com.google.android.exoplayer:exoplayer:2.18.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.18.1")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.18.1")
    implementation("com.google.android.exoplayer:exoplayer-dash:2.18.1")
    implementation("com.google.android.exoplayer:exoplayer-rtsp:2.18.1")

    // CardView pour afficher les tuiles de station
    implementation("androidx.cardview:cardview:1.0.0")

    // Dépendances Compose (Mise à jour pour compatibilité avec Kotlin 1.9.21)
    implementation(platform("androidx.compose:compose-bom:2023.05.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Dépendances Android Auto
    implementation("androidx.car.app:app:1.2.0")

    // Dépendances de test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")


}
