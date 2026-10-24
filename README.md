# Climatin

App Android de clima con widgets Material You, al estilo de los Pixel.

- **Kotlin + Jetpack Compose** con Material 3 y colores dinámicos (toman el fondo de pantalla en Android 12+).
- **Cielo animado** detrás del pronóstico: sol, nubes, lluvia, nieve, niebla, estrellas o relámpagos según el clima.
- **Varias ciudades**: buscador, se desliza entre ellas, se ordenan y se borran en *Ciudades y ajustes*.
- **Datos**: temperatura por hora en gráfico, 7 días, humedad, viento, UV, calidad del aire, presión, visibilidad, amanecer/atardecer y fase lunar.
- **Aviso de lluvia**: "Lluvia en unos 30 min" en la app y en "De un vistazo"; notificación opcional.
- **Widgets con Glance** (cada uno elige ciudad y estilo al agregarlo o con el lápiz):
  - **Clima ahora**: responsive, de 1x1 (ícono y temperatura) a 4x2 (ciudad, máx/mín y próximas horas).
  - **Pronóstico**: estado actual y próximos días; si lo agrandás, también las próximas horas.
  - **De un vistazo**: fecha y clima sin fondo, como el del Pixel.
  - **Gráfico**: curva de temperatura de las próximas horas.
  - **Burbuja**: círculo con ícono, temperatura y máx/mín.
  - **Píldora**: una línea con ícono, temperatura y estado.
  - **Semana**: los próximos días en lista.
  - **Sol**: arco de amanecer a atardecer con la posición del sol.
- **Estilos de widget**: Sistema (colores del fondo de pantalla), Color (más intenso) o Transparente (texto blanco sobre el fondo de pantalla). Se eligen con vista previa al agregar el widget.
- **Vistas previas reales** en el selector de widgets, con los colores del sistema en claro y oscuro.
- **Datos**: [Open-Meteo](https://open-meteo.com) (pronóstico, calidad del aire y buscador de ciudades), gratis y sin API key.
- **Ubicación aproximada** (`ACCESS_COARSE_LOCATION`), y opcionalmente "todo el tiempo" para que los widgets te sigan con la app cerrada.
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

## Ubicación en segundo plano

Sin el permiso "Permitir todo el tiempo", Android no siempre entrega la ubicación con la app cerrada y el widget usa la última conocida. Se activa en *Ciudades y ajustes → Ubicación con la app cerrada*.
