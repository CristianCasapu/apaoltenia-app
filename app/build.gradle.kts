import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Semnare release: datele keystore-ului sunt citite din keystore.properties
// (gitignored) sau din variabile de mediu (folosite de GitHub Actions).
// Astfel, cheia si parolele NU ajung niciodata in cod / pe GitHub.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(keystorePropertiesFile.inputStream())
    }
}

fun signingValue(propKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: System.getenv(envKey)

android {
    namespace = "ro.apaoltenia.client"
    compileSdk = 34

    defaultConfig {
        applicationId = "ro.apaoltenia.client"
        minSdk = 30          // Redmi Note 13 Pro ships with Android 13 (API 33)
        targetSdk = 34
        versionCode = 4
        versionName = "1.1.0"

        // Optimizat pentru Xiaomi Redmi Note 13 Pro (Snapdragon / arm64-v8a).
        // Aplicatia e bazata pe WebView (fara librarii native .so), deci arm64
        // este suportat implicit; filtrul de mai jos e pentru orice librarie
        // nativa viitoare si pentru un APK curat.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    val storeFilePath = signingValue("storeFile", "KEYSTORE_FILE")
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Semneaza cu configuratia release doar daca exista un keystore.
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // WebView modern (algorithmic darkening, safe browsing) prin androidx.webkit
    implementation("androidx.webkit:webkit:1.11.0")

    // Autentificare biometrica: amprenta, fata, PIN, model, parola dispozitiv
    implementation("androidx.biometric:biometric:1.1.0")

    // Verificarea periodica a facturilor in fundal
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
