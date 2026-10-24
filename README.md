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
- **Radar de lluvia** animado ([RainViewer](https://www.rainviewer.com) sobre mapas de CARTO/OpenStreetMap).
- **Alertas del SMN** para lugares en Argentina, en la app y como notificación opcional.
- **Detalle por día** (curva por hora, lluvia total, viento máximo, UV) y pronóstico de **14 días**.
- **Unidades**: °C/°F, km/h, m/s, mph o nudos, hPa, inHg o mmHg, y 12/24 h.
- **Fondo de pantalla animado** con el cielo del clima real, y **botón de ajustes rápidos**.
- **Wear OS** (módulo `wear`): tarjeta y complicación con el clima del reloj.
- **Datos**: [Open-Meteo](https://open-meteo.com) (pronóstico, calidad del aire y buscador de ciudades), gratis y sin API key.
- **Ubicación aproximada** (`ACCESS_COARSE_LOCATION`), y opcionalmente "todo el tiempo" para que los widgets te sigan con la app cerrada.
- **Actualización**: WorkManager cada 30 minutos, solo con conexión.

## Instalar el APK

Cada push a `main` compila el APK en GitHub Actions. Entrá a **Actions → Build APK → la última corrida → Artifacts → `clima-apk`**, bajalo e instalalo en el teléfono (hay que permitir "instalar apps desconocidas").

- `app-debug.apk`: para probar.
- `app-release.apk`: minificado y firmado con la clave de debug (o con la propia, si está configurada).
- `wear-release.apk`: la app del reloj. Se instala con `adb install` sobre el reloj en modo depuración.

## Compilar en tu máquina

Con Android Studio (Ladybug o posterior) abrí la carpeta y dale Run. Por consola:

```bash
./gradlew assembleDebug
```

Requiere JDK 17 y el SDK de Android 35.

## Estructura

```
core/   Modelos, cliente de Open-Meteo, formatos, unidades e íconos (compartido; tiene tests)
app/    App del teléfono
├── data/        Ubicación, caché, alertas del SMN y radar
├── ui/          Pantallas Compose, cielo animado y tema Material You
├── widget/      Widgets Glance y su configuración
├── wallpaper/   Fondo de pantalla animado
├── tile/        Botón de ajustes rápidos
└── work/        Refresco periódico y notificaciones
wear/   App de Wear OS (tarjeta y complicación)
```

Los tests corren con `./gradlew testDebugUnitTest`. Para publicar en Play Store, ver [PUBLICAR.md](PUBLICAR.md).

## Ubicación en segundo plano

Sin el permiso "Permitir todo el tiempo", Android no siempre entrega la ubicación con la app cerrada y el widget usa la última conocida. Se activa en *Ciudades y ajustes → Ubicación con la app cerrada*.
