plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// App de Wear OS: tarjeta (tile) y complicación con el clima, independiente del teléfono.
android {
    namespace = "com.mnavarrosqa.clima.wear"
    compileSdk = 36

    defaultConfig {
        // Mismo id que la app del teléfono, como pide Play para publicarlas juntas.
        applicationId = "com.mnavarrosqa.clima"
        minSdk = 30
        targetSdk = 36
        versionCode = 2_000_055
        versionName = "1.25.0"
    }

    signingConfigs {
        // Clave de prueba fija (en el repo) para que cada APK del CI se pueda instalar encima del
        // anterior. Sin esto, cada compilación genera una clave distinta y Android rechaza la
        // actualización. No sirve para Play Store: para eso está la clave "release".
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Clave propia para Play Store, si el CI (o tu máquina) define las variables. Ver PUBLICAR.md.
        create("release") {
            System.getenv("CLIMATIN_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("CLIMATIN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CLIMATIN_KEY_ALIAS")
                keyPassword = System.getenv("CLIMATIN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: el APK del reloj traía Guava y los servicios de Google enteros.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sin clave propia se firma con la de debug, para poder instalarlo a mano.
            signingConfig = signingConfigs.getByName(
                if (System.getenv("CLIMATIN_KEYSTORE") != null) "release" else "debug",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // La pantalla de la app del reloj está hecha con Compose para Wear OS.
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Compose para Wear (Material 2: API estable). Trae ui, foundation y runtime de Compose.
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-material:1.4.0")
    // Alguna dependencia de Wear trae un Fragment viejo; las APIs de resultados piden 1.3+.
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.guava:guava:33.3.1-android")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Recibe del teléfono el pronóstico, las unidades y el nombre del lugar.
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
