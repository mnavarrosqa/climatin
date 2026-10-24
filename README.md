# Climatin

App Android de clima con widgets Material You, al estilo de los Pixel.

- **Kotlin + Jetpack Compose** con Material 3 y colores dinámicos (toman el fondo de pantalla en Android 12+).
- **Widgets con Glance**:
  - **Clima ahora**: responsive. De 1x1 (ícono y temperatura) a 4x2 (ciudad, máx/mín y próximas horas).
  - **Pronóstico**: estado actual y próximos días; si lo agrandás, también las próximas horas.
- **Datos**: [Open-Meteo](https://open-meteo.com), gratis y sin API key.
- **Ubicación aproximada** (`ACCESS_COARSE_LOCATION`). Alcanza para el clima y es menos invasiva.
- **Actualización**: WorkManager cada 30 minutos, solo con conexión.

## Instalar el APK

Cada push a `main` compila el APK en GitHub Actions. Entrá a **Actions → Build APK → la última corrida → Artifacts → `clima-apk`**, bajalo e instalalo en el teléfono (hay que permitir "instalar apps desconocidas").

- `app-debug.apk`: para probar.
- `app-release.apk`: minificado y firmado con la clave de debug. Para publicarlo en Play Store hay que configurar una clave propia.

## Compilar en tu máquina

Con Android Studio (Ladybug o posterior) abrí la carpeta y dale Run. Por consola:

```bash
./gradlew assembleDebug
```

Requiere JDK 17 y el SDK de Android 35.

## Estructura

```
app/src/main/java/com/mnavarrosqa/clima/
├── data/        Open-Meteo, ubicación, caché (SharedPreferences) y códigos WMO
├── ui/          Pantalla principal (Compose) y tema Material You
├── widget/      Widgets Glance y sus receivers
└── work/        Refresco periódico con WorkManager
```

## Limitación conocida

Desde segundo plano Android no siempre entrega la ubicación sin el permiso de ubicación "todo el tiempo". En ese caso el widget usa la última ubicación conocida, que se actualiza cada vez que abrís la app.
