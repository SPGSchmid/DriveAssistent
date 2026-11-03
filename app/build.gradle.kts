import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

// ── Keystore-Konfiguration aus lokaler keystore.properties laden ──────────────
// Die Datei liegt im Projektverzeichnis und ist in .gitignore eingetragen.
// Fehlt sie (z.B. auf CI), wird der Release-Build ohne Signierung gebaut
// und schlägt beim Upload in den Store fehl (Sideload funktioniert trotzdem).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.driveassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.driveassistant"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // Room Schema-Export für Migrations
        kapt {
            arguments {
                arg("room.schemaLocation", "$projectDir/schemas")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    // ── Signing ───────────────────────────────────────────────────────────────
    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile     = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias      = keystoreProperties["keyAlias"] as String
                keyPassword   = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            // Debug-Builds verwenden automatisch den Debug-Keystore von Android Studio
        }
    }

    // Kotlin-Source-Ordner
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.runtime)
    implementation(libs.coroutines.android)

    // ── Android for Cars App Library ──────────────────────────────────────
    // Beinhaltet CarAppService, Session, Screen, CarContext, Action, ActionStrip
    implementation(libs.car.app)

    // ── Networking (Retrofit + OkHttp + Gson) ─────────────────────────────
    // Wird für OpenAI GPT-4 API (Sub-Task 1.3), MS Graph API (1.4/1.5),
    // HubSpot API (Phase 3) und Spotify Web API (Phase 4) benötigt.
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)   // HTTP-Log für Debug-Builds
    implementation(libs.gson)

    // ── Microsoft Authentication Library (MSAL) ───────────────────────────
    // OAuth 2.0 für Microsoft 365 (Mail, Calendar, Teams) – Sub-Task 1.4
    implementation(libs.msal) {
        // Transitive Konflikte mit Retrofit-OkHttp-Version vermeiden
        exclude(group = "com.squareup.okhttp3")
        // com.microsoft.device.display ist nur für Dual-Screen-Geräte (Surface Duo)
        // und nicht in einem öffentlichen Maven-Repository verfügbar
        exclude(group = "com.microsoft.device.display")
        // opentelemetry-bom ist eine Java-Platform (BOM), keine Android-Library –
        // inkompatibel mit dem Android-Variant-Matching
        exclude(group = "io.opentelemetry")
    }

    // ── Security (EncryptedSharedPreferences) ─────────────────────────────
    // Sichere Speicherung von API-Keys und OAuth-Tokens – Sub-Task 1.3 & 5.1
    implementation(libs.security.crypto)

    // ── Room (Offline-Cache + PLAUD Import-Tracking) ──────────────────────
    // Mail/Termin-Cache für Offline-Modus (Sub-Task 1.5)
    // Import-Status-DB für PLAUD-Transkripte (Sub-Task 3.2)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // ── WorkManager (Background Jobs) ─────────────────────────────────────
    // PLAUD-Datei-Monitoring + Polling für Teams-Nachrichten – Sub-Task 3.2 & 2.2
    implementation(libs.work.runtime)
}
