plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.plugin)
}

android {
    namespace = "com.zap.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zap.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // Caminho original (Windows). No Linux o Gradle não converte "C:/..." para File,
            // o que quebra a configuração do projeto inteiro (inclusive tasks de debug/teste).
            // Mantém o mesmo caminho/passwords no Windows e usa o keystore de debug padrão
            // do Android fora do Windows. Alteração autorizada na Etapa 06.
            val windowsKeystore = "C:/Users/SnyX/.android/debug.keystore"
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            storeFile = if (isWindows) {
                file(windowsKeystore)
            } else {
                file("${System.getProperty("user.home")}/.android/debug.keystore")
            }
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    // Compose TV
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Image
    implementation(libs.coil.compose)

    // JSON
    implementation(libs.gson)

    // Player
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)

    // WorkManager
    implementation(libs.work.runtime)

    // Navigation
    implementation(libs.navigation.compose)

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Tests (unit - JVM)
    testImplementation(libs.junit)
}

// ksp room schema disabled for now

// Catálogo M3U será gerado manualmente e commitado no repo
// Task desabilitada - catálogo já commitado em assets/m3u_catalog.json
