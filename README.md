# Climatin

App Android de clima con widgets Material You, al estilo de los Pixel.

- **Kotlin + Jetpack Compose** con Material 3 y colores dinámicos (toman el fondo de pantalla en Android 12+).
- **Cielo animado** detrás del pronóstico: sol, nubes, lluvia, nieve, niebla, estrellas o relámpagos según el clima.
- **Varias ciudades**: buscador, se desliza entre ellas. En *Ciudades y ajustes* son tarjetas con su cielo y temperatura: se quitan deslizando (con deshacer) y se reordenan manteniéndolas apretadas.
- **Datos**: temperatura por hora en gráfico (tocá una hora para ver sensación térmica, viento y lluvia), humedad, viento, UV, calidad del aire, presión, visibilidad, amanecer/atardecer y fase lunar, con medidores, brújula, arco del sol y dibujo de la luna.
- **Diseño Material 3 Expressive**: tipografía Google Sans Flex (descargable, con Roboto Flex de respaldo), encabezado que se achica al bajar, carga con esqueleto animado, vibración leve y gesto "atrás" predictivo.
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
  - **Próxima hora**: barras de lluvia cada 15 minutos.
  - **Ciudades**: todas tus ciudades en una lista.
  - **Indicador**: índice UV, calidad del aire, humedad o viento (con brújula) en un medidor.
- **En los widgets**: frase del momento ("Llevá paraguas a las 17", "UV alto", "Mañana 6° más fresco"…), ⟳ para actualizar, › para pasar de ciudad, y tocar un día u hora abre su detalle en la app.
- **Estilos de widget**: Sistema (colores del fondo de pantalla), Color (más intenso), Transparente (texto blanco sobre el fondo de pantalla) o Cielo (degradado del clima actual). Se eligen con vista previa al agregar el widget.
- **Vistas previas reales** en el selector de widgets, con los colores del sistema en claro y oscuro; en Android 15+, con tus datos.
- **Mapa de lluvia** animado: la lluvia de Open-Meteo (de 3 horas atrás a 9 adelante, sin clave) sobre el mapa gris de Esri. Es lluvia calculada por el modelo, no radar.
- **Alertas del SMN** para lugares en Argentina, en la app y como notificación opcional.
- **Detalle por día** (curva por hora, lluvia total, viento máximo, UV) y pronóstico de **14 días**.
- **Unidades**: °C/°F, km/h, m/s, mph o nudos, hPa, inHg o mmHg, y 12/24 h.
- **Fondo de pantalla animado** con el cielo del clima real, y **botón de ajustes rápidos**.
- **Wear OS** (módulo `wear`): tarjeta y complicación con el clima del reloj.
- **Plegables y tablets**: en pantallas anchas el pronóstico va en dos paneles.
- **Temperatura en la barra de estado** (opcional), con notificación silenciosa.
- **Español e inglés** según el idioma del teléfono (o el elegido para la app en Android 13+).
- **Datos**: [Open-Meteo](https://open-meteo.com) (pronóstico, calidad del aire y buscador de ciudades), gratis y sin API key.
- **Ubicación aproximada** (`ACCESS_COARSE_LOCATION`), y opcionalmente "todo el tiempo" para que los widgets te sigan con la app cerrada.
- **Actualización**: WorkManager cada 30 minutos, solo con conexión.

## Actualizaciones

Cada versión nueva (cuando cambia `versionName`) se publica sola en [climatin-releases](https://github.com/mnavarrosqa/climatin-releases/releases), un repo público que solo tiene los APK. La app consulta ahí cada 12 horas y avisa; al tocar Actualizar la baja sola (con barra de avance) y abre el instalador de Android (la primera vez pide permitir "Instalar apps desconocidas"); también se puede buscar a mano en *Ciudades y ajustes → Acerca de*. La app tiene dos variantes: `github` (la que se instala a mano, con esta actualización) y `play` (para la tienda, sin el permiso de instalar apps; ahí actualiza Play).

Para que funcione, el repo `climatin` necesita el secreto `RELEASES_TOKEN`: un token de GitHub con permiso de escritura de contenido (*Contents: Read and write*) solo sobre `climatin-releases`.

Los APK se firman con una clave de prueba fija (`keystore/debug.keystore`), así cada versión se instala encima de la anterior. Para Play Store se usa la clave propia (ver [PUBLICAR.md](PUBLICAR.md)).

## Instalar el APK

Cada push a `main` compila el APK en GitHub Actions. Entrá a **Actions → Build APK → la última corrida → Artifacts → `clima-apk`**, bajalo e instalalo en el teléfono (hay que permitir "instalar apps desconocidas").

- `app-debug.apk`: para probar.
- `app-github-release.apk`: minificado y firmado con la clave de debug (o con la propia, si está configurada).
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

Los tests corren con `./gradlew :core:testDebugUnitTest`. Las capturas de pantalla (teléfono, plegable, tablet; claro, oscuro; español, inglés) se generan con `./gradlew :app:testDebugUnitTest` en `app/build/capturas/`, y el CI las deja en el artifact `capturas`. Para publicar en Play Store, ver [PUBLICAR.md](PUBLICAR.md).

## Ubicación en segundo plano

Sin el permiso "Permitir todo el tiempo", Android no siempre entrega la ubicación con la app cerrada y el widget usa la última conocida. Se activa en *Ciudades y ajustes → Ubicación con la app cerrada*.
