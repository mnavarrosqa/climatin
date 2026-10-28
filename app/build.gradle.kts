plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.mnavarrosqa.clima"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mnavarrosqa.clima"
        minSdk = 26
        targetSdk = 36
        versionCode = 77
        // Solo español e inglés: evita que las librerías sumen traducciones que la app no tiene.
        resourceConfigurations += listOf("es", "en")
        versionName = "1.34.2"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Dos variantes: "github" se instala a mano y se actualiza sola desde climatin-releases (pide
    // permiso para instalar apps); "play" va a la tienda, sin ese permiso, y la actualiza Play.
    flavorDimensions += "distribucion"
    productFlavors {
        create("github") {
            dimension = "distribucion"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            // Siempre con la clave fija del repo: si cambiara, Android rechazaría la actualización
            // dentro de la app en los teléfonos que ya la tienen instalada.
            signingConfig = signingConfigs.getByName("debug")
        }
        create("play") {
            dimension = "distribucion"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            // La clave propia para Play Store, si el CI la define (ver PUBLICAR.md); si no, la de
            // prueba, para poder instalarlo a mano.
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Tests de capturas de pantalla (Robolectric + Roborazzi): dibujan la app sin teléfono y
    // guardan las imágenes en app/build/capturas.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Pantalla de inicio animada (y la misma en Android viejos, con el ícono quieto).
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    // Para pasarle el pronóstico al reloj (Wear OS Data Layer).
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.32.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.32.2")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
