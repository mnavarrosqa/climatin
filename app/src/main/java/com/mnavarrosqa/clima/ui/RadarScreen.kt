package com.mnavarrosqa.clima.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.mnavarrosqa.clima.data.Coordinates
import com.mnavarrosqa.clima.data.LocationProvider
import com.mnavarrosqa.clima.data.Place
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Geo
import com.mnavarrosqa.clima.data.RadarImage
import com.mnavarrosqa.clima.data.RadarList
import com.mnavarrosqa.clima.data.RainFrame
import com.mnavarrosqa.clima.data.RainMap
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.mmText
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

private data class Tile(val x: Int, val y: Int)

/** Punto del mapa que se mantuvo apretado: dónde queda y dónde se dibuja. */
private class MapPoint(val latitude: Double, val longitude: Double, val screen: Offset)

/** Lluvia cargada para una zona del mapa (en coordenadas de tesela de su [zoom]). */
private class RainArea(
    val x0: Double,
    val y0: Double,
    val x1: Double,
    val y1: Double,
    val zoom: Int,
    val frames: List<RainFrame>,
    val overlays: List<ImageBitmap>,
)

/** Un lugar marcado en el mapa: tus ciudades y donde estás. */
private class MapPin(val name: String, val latitude: Double, val longitude: Double, val here: Boolean)

/** Lo que hay en pantalla: lo que midió el radar o lo que calcula el modelo, y de qué hora. */
private data class Shown(val radar: Boolean, val epochSeconds: Long)

/**
 * Lo último que se cargó del modelo. Vive con el proceso: al volver a la pestaña del mapa no se
 * le pide de nuevo a Open-Meteo, que limita las consultas por minuto.
 */
private var keptArea: Pair<Long, RainArea>? = null
private const val KEPT_AREA_MS = 10 * 60_000L

/**
 * Teselas del radar que se tienen a la vez (las de la pantalla por cada imagen de las dos horas).
 * Si no entran, el radar se pide con menos zoom y se estira: son menos descargas y menos memoria.
 */
private const val RADAR_TILE_BUDGET = 160

/** Margen alrededor de la primera y la última imagen del radar en que todavía se muestra. */
private const val RADAR_SLACK_SECONDS = 5 * 60L

/**
 * Mapa de lluvia animado (3 horas atrás y 12 adelante). Hasta ahora se ve lo que midió el radar
 * (RainViewer, cada 10 minutos, donde hay cobertura); de ahí en adelante, y donde el radar no
 * muestra nada, lo que calcula el modelo de Open-Meteo. Se arrastra, se pellizca para acercar,
 * doble toque acerca y tocar un punto dice si llueve ahí a esa hora. Muestra tus ciudades.
 * Mantener apretado un punto ofrece agregarlo a tus ciudades ([onAddPlace]).
 *
 * Es una pestaña de la barra: no tiene flecha de volver y [onBack] no se usa (el botón atrás del
 * sistema lo atiende la raíz). El centro, el zoom y la hora elegida se guardan al cambiar de pestaña.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun RadarScreen(latitude: Double, longitude: Double, onBack: () -> Unit, onAddPlace: ((Place) -> Unit)? = null) {
    // El tema elegido en la app (claro u oscuro), no el del sistema.
    val dark = LocalDarkTheme.current
    val context = LocalContext.current
    // Vista: zoom entero de las teselas, escala del pellizco (0,75 a 1,5) y centro en teselas.
    // Con el lugar como clave: al abrir el mapa de otra ciudad no vuelve a la vista de la anterior.
    var zoom by rememberSaveable(latitude, longitude) { mutableIntStateOf(7) }
    var scale by rememberSaveable(latitude, longitude) { mutableFloatStateOf(1f) }
    var centerX by rememberSaveable(latitude, longitude) { mutableDoubleStateOf(Geo.tileX(longitude, 7)) }
    var centerY by rememberSaveable(latitude, longitude) { mutableDoubleStateOf(Geo.tileY(latitude, 7)) }
    var area by remember {
        mutableStateOf(keptArea?.takeIf { System.currentTimeMillis() - it.first < KEPT_AREA_MS }?.second)
    }
    // La hora que se estaba viendo (epoch), para volver a ella al regresar a la pestaña. En un
    // arreglo y no en un estado: se escribe en cada cuadro y nadie tiene que recomponerse por eso.
    val savedTime = rememberSaveable { longArrayOf(-1L) }
    // Posición en la línea de tiempo, continua (2,4 es "entre las 2 y las 3"): la lluvia se funde de
    // una hora a la otra en vez de saltar.
    val position = remember { Animatable(startPosition(area?.frames.orEmpty(), savedTime[0], null)) }
    val frameIndex by remember { derivedStateOf { position.value.roundToInt() } }
    val scope = rememberCoroutineScope()
    var playing by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    // Sube para volver a intentar después de un error.
    var attempt by remember { mutableIntStateOf(0) }
    // El último intento atendido: "Reintentar" carga aunque lo que se ve ya esté cubierto.
    var lastAttempt by remember { mutableIntStateOf(0) }
    // Lo que dice el punto tocado: "Lluvia moderada · 2,1 mm".
    var probe by remember { mutableStateOf<Pair<Offset, String>?>(null) }
    // Radar: la lista de imágenes (null si no se pudo bajar: queda solo el modelo), las teselas
    // que se usan ahora y si alguna trae algo. Sin nada en ninguna (zona sin cobertura, o seca
    // las dos horas) se muestra el modelo, como antes.
    var radarList by remember { mutableStateOf<RadarList?>(null) }
    val radarImages = remember { mutableStateMapOf<String, ImageBitmap>() }
    var radarSeen by remember { mutableStateOf(false) }
    var radarLoading by remember { mutableStateOf(false) }
    val cached = remember { WeatherRepository.get(context).allCached() }
    val snapshot = remember(latitude, longitude) {
        cached.firstOrNull { abs(it.latitude - latitude) < 0.01 && abs(it.longitude - longitude) < 0.01 }
    }
    // Las horas del mapa en la zona del lugar que se mira, no en la del teléfono.
    val zone = remember(snapshot) {
        snapshot?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }
    // El nombre que va arriba, donde antes estaba la flecha.
    val placeName = if (snapshot == null) tr("Mapa de lluvia", "Rain map") else snapshot.place ?: tr("Mi ubicación", "My location")
    // Las ciudades agregadas desde el mapa se marcan enseguida, sin esperar su pronóstico.
    val addedPins = remember { mutableStateListOf<MapPin>() }
    val pins = remember(addedPins.size) {
        cached.map { MapPin(it.place ?: tr("Mi ubicación", "My location"), it.latitude, it.longitude, it.placeId == CURRENT_PLACE_ID) } + addedPins
    }
    // Punto apretado para agregar como ciudad, y su nombre cuando llega del geocodificador.
    var adding by remember { mutableStateOf<MapPoint?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val view = LocalView.current
    adding?.let { point ->
        AddPlaceDialog(
            point = point,
            onDismiss = { adding = null },
            onConfirm = { name ->
                adding = null
                val add = onAddPlace ?: return@AddPlaceDialog
                // Redondeado a ~1 km: tocar dos veces casi el mismo punto no la duplica.
                val id = String.format(java.util.Locale.US, "map:%.2f,%.2f", point.latitude, point.longitude)
                add(Place(id = id, name = name, detail = null, latitude = point.latitude, longitude = point.longitude))
                addedPins += MapPin(name, point.latitude, point.longitude, here = false)
                scope.launch { snackbar.showSnackbar(tr("Agregaste $name", "Added $name"), duration = SnackbarDuration.Short) }
            },
        )
    }
    val images = remember { mutableStateMapOf<String, ImageBitmap>() }
    val tileScope = rememberCoroutineScope()
    val tilesInFlight = remember { mutableSetOf<String>() }
    val frames = area?.frames.orEmpty()

    // La lista del radar cambia cada 10 minutos: se vuelve a pedir mientras el mapa está abierto.
    // Si falla queda la anterior, o ninguna.
    LaunchedEffect(attempt) {
        while (true) {
            RainMap.radarList()?.let { radarList = it }
            delay(5 * 60_000L + 5_000L)
        }
    }
    // Se anota la hora que se ve para volver a ella al regresar a la pestaña.
    LaunchedEffect(frames) {
        if (frames.isEmpty()) return@LaunchedEffect
        snapshotFlow { position.value }.collect { savedTime[0] = timeAt(frames, it) }
    }

    // Reproducir: avanza parejo, una hora cada 0,9 s; al final espera un poco y vuelve al principio.
    LaunchedEffect(playing, frames.size) {
        if (!playing || frames.size < 2) return@LaunchedEffect
        val last = frames.lastIndex.toFloat()
        while (true) {
            if (position.value >= last - 0.001f) {
                delay(1200)
                position.snapTo(0f)
            }
            val target = (floor(position.value) + 1f).coerceAtMost(last)
            position.animateTo(target, tween(((target - position.value) * 900).roundToInt().coerceAtLeast(16), easing = LinearEasing))
        }
    }
    val scrub: (Float) -> Unit = { value ->
        playing = false
        probe = null
        scope.launch { position.snapTo(value.coerceIn(0f, frames.lastIndex.coerceAtLeast(0).toFloat())) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // Cada tesela de 256 px se dibuja al doble para que se lea en pantallas densas.
        val tilePx = 512f * scale
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val worldSize = (1 shl zoom).toDouble()
        val halfW = widthPx / 2 / tilePx
        val halfH = heightPx / 2 / tilePx
        val tiles = remember(zoom, floor(centerX - halfW), floor(centerY - halfH), ceil(centerX + halfW), ceil(centerY + halfH)) {
            val max = (1 shl zoom) - 1
            buildList {
                for (x in floor(centerX - halfW).toInt()..ceil(centerX + halfW).toInt()) {
                    for (y in floor(centerY - halfH).toInt()..ceil(centerY + halfH).toInt()) {
                        if (y in 0..max) add(Tile(x, y))
                    }
                }
            }
        }

        // Las teselas se bajan en un alcance que no se cancela al moverse: si el arrastre cruza
        // un borde, la que ya venía bajando se guarda igual en vez de pedirse de nuevo.
        LaunchedEffect(tiles, dark) {
            val urls = tiles.map { RainMap.baseTileUrl(dark, zoom, Math.floorMod(it.x, 1 shl zoom), it.y) }
            // Al moverse quedan teselas viejas: se descartan para no acumular memoria.
            if (images.size > 160) images.keys.retainAll(urls.toSet())
            urls.filter { it !in images && it !in tilesInFlight }.forEach { url ->
                tilesInFlight += url
                tileScope.launch {
                    RainMap.tile(url)?.let { images[url] = it }
                    tilesInFlight -= url
                }
            }
        }

        // La lluvia se pide para lo visible con un margen; se vuelve a pedir recién cuando lo que
        // se ve sale de lo cargado o cambia el zoom (con una pausa, para no pedir en cada arrastre).
        val loaded = area
        val factor = loaded?.let { 2.0.pow(zoom - it.zoom) } ?: 1.0
        // Cubierto si lo que se ve entra en lo cargado, aunque sea con otro zoom; al acercarse
        // más de un paso la grilla queda gruesa y se pide de nuevo.
        // Arriba y abajo, si lo cargado llega al borde del mundo, más allá no hay nada que pedir.
        val covered = loaded != null && factor <= 2.0 &&
            (centerX - halfW) / factor >= loaded.x0 && (centerX + halfW) / factor <= loaded.x1 &&
            (loaded.y0 <= 0.0 || (centerY - halfH) / factor >= loaded.y0) &&
            (loaded.y1 >= (1 shl loaded.zoom).toDouble() || (centerY + halfH) / factor <= loaded.y1)
        val wantKey = if (covered) null else Triple(zoom, (centerX * 2).roundToInt(), (centerY * 2).roundToInt())
        LaunchedEffect(wantKey, attempt) {
            if (wantKey == null && loaded != null && attempt == lastAttempt) {
                // Lo que se ve ya está cargado: un error de antes ya no corresponde.
                error = null
                return@LaunchedEffect
            }
            lastAttempt = attempt
            // Siempre con una pausa: cada carga son muchos puntos para Open-Meteo, que limita las
            // consultas por minuto, y mientras se arrastra no tiene sentido pedir.
            delay(if (loaded == null && attempt == 0) 150 else 400)
            error = null
            loading = true
            try {
                val x0 = centerX - halfW * 1.6
                val x1 = centerX + halfW * 1.6
                val y0 = (centerY - halfH * 1.4).coerceAtLeast(0.0)
                val y1 = (centerY + halfH * 1.4).coerceAtMost(worldSize)
                val requestZoom = zoom
                runCatching { RainMap.load(x0, y0, x1, y1, requestZoom) }
                    .onSuccess { result ->
                        val first = area == null
                        val fresh = RainArea(x0, y0, x1, y1, requestZoom, result, result.map(RainMap::overlay))
                        area = fresh
                        keptArea = System.currentTimeMillis() to fresh
                        if (first) {
                            // Arranca en la hora que se estaba viendo, o en la actual.
                            position.snapTo(startPosition(result, savedTime[0], null))
                        } else if (position.value > result.lastIndex) {
                            position.snapTo(startPosition(result, -1L, null))
                        }
                    }
                    .onFailure { failure ->
                        // Si se canceló porque se movió el mapa, no es un error: lo carga el efecto nuevo.
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        error = if (failure is RainMap.TooManyRequests) {
                            tr("Open-Meteo pide una pausa: probá de nuevo en un minuto.", "Open-Meteo asks for a break: try again in a minute.")
                        } else {
                            tr("No pude cargar el mapa de lluvia. Revisá la conexión.", "Couldn't load the rain map. Check your connection.")
                        }
                    }
            } finally {
                // También si se canceló: si no, "Actualizando…" podía quedar para siempre.
                loading = false
            }
        }

        // Radar: se pide con el zoom del mapa (hasta el máximo gratuito) o con menos si las teselas
        // de todas las imágenes no entran en el tope. Depende del zoom y del tamaño de la pantalla,
        // no de dónde está el centro: arrastrar no lo cambia.
        val radar = radarList
        val radarCount = radar?.frames?.size ?: 0
        val radarZoom = radarZoomFor(zoom, widthPx, heightPx, radarCount)
        // Cuántas teselas del mapa ocupa de lado una del radar.
        val radarFactor = 2.0.pow(zoom - radarZoom)
        val radarX0 = floor((centerX - halfW) / radarFactor).toInt()
        val radarX1 = floor((centerX + halfW) / radarFactor).toInt()
        val radarY0 = floor((centerY - halfH) / radarFactor).toInt().coerceAtLeast(0)
        val radarY1 = floor((centerY + halfH) / radarFactor).toInt().coerceAtMost((1 shl radarZoom) - 1)
        val radarTiles = remember(radarX0, radarX1, radarY0, radarY1) {
            buildList {
                for (x in radarX0..radarX1) {
                    for (y in radarY0..radarY1) add(Tile(x, y))
                }
            }
        }
        // Dirección de cada tesela, por imagen: se arma una vez y no en cada cuadro del dibujo.
        val radarUrls = remember(radar, radarTiles, radarZoom) {
            if (radar == null) {
                emptyList()
            } else {
                val world = 1 shl radarZoom
                radar.frames.map { image ->
                    radarTiles.map { RainMap.radarTileUrl(radar.host, image.path, radarZoom, Math.floorMod(it.x, world), it.y) }
                }
            }
        }
        LaunchedEffect(radarUrls) {
            if (radarUrls.isEmpty()) return@LaunchedEffect
            val wanted = HashSet<String>()
            radarUrls.forEach { wanted.addAll(it) }
            // Lo que ya no se ve se suelta; lo que quedó en memoria de antes se toma sin bajar.
            radarImages.keys.retainAll(wanted)
            wanted.forEach { url ->
                if (url !in radarImages) RainMap.radarCached(url)?.let { radarImages[url] = it }
            }
            radarSeen = radarImages.values.any { !RainMap.isBlank(it) }
            // De la imagen más nueva a la más vieja: lo de ahora es lo primero que se quiere ver.
            val missing = radarUrls.asReversed().flatten().distinct().filter { it !in radarImages }
            if (missing.isEmpty()) return@LaunchedEffect
            // Con una pausa, para no pedir mientras se arrastra.
            delay(300)
            radarLoading = true
            try {
                val gate = Semaphore(4)
                coroutineScope {
                    missing.forEach { url ->
                        launch {
                            gate.withPermit {
                                RainMap.radarTile(url)?.let { tile ->
                                    radarImages[url] = tile
                                    if (!RainMap.isBlank(tile)) radarSeen = true
                                }
                            }
                        }
                    }
                }
            } finally {
                radarLoading = false
            }
        }
        // Las imágenes del radar que se usan: ninguna si no trajo nada para lo que se ve.
        val radarFrames: List<RadarImage> = if (radar != null && radarSeen) radar.frames else emptyList()
        // Qué hay en pantalla. Cambia unas pocas veces por vuelta, no en cada cuadro.
        val shown by remember(frames, radarFrames) {
            derivedStateOf {
                val index = radarPosition(radarFrames, timeAt(frames, position.value))?.roundToInt()
                if (index != null) {
                    Shown(true, radarFrames[index.coerceIn(0, radarFrames.lastIndex)].epochSeconds)
                } else {
                    Shown(false, frames.getOrNull(position.value.roundToInt())?.epochSeconds ?: -1L)
                }
            }
        }
        // "Ahora" en la línea de tiempo: la última imagen del radar si hay, o la hora en curso.
        val nowPosition = startPosition(frames, -1L, radarFrames.lastOrNull()?.epochSeconds)
        val atNow by remember(nowPosition) { derivedStateOf { abs(position.value - nowPosition) < 0.5f } }

        val markerColor = MaterialTheme.colorScheme.primary
        val measurer = rememberTextMeasurer()
        val pinStyle = TextStyle(
            color = if (dark) Color.White else Color(0xFF1B1D21),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        // Borde del color del mapa alrededor de cada nombre: sin él no se leía sobre calles y lluvia.
        val pinHalo = if (dark) Color(0xE61B1D21) else Color(0xE6FFFFFF)
        // Los nombres se miden una vez: el mapa se redibuja en cada cuadro mientras se reproduce.
        val pinLabels = remember(pins, pinStyle) { pins.map { measurer.measure(it.name, pinStyle) } }

        /** Lluvia en un punto de la pantalla ([w] x [h]), en la hora que se ve. Lee el estado actual. */
        fun rainAt(point: Offset, w: Float, h: Float): Float? {
            val a = area ?: return null
            val f = 2.0.pow(zoom - a.zoom)
            val tile = 512f * scale
            val tx = (centerX + (point.x - w / 2) / tile) / f
            val ty = (centerY + (point.y - h / 2) / tile) / f
            val col = ((tx - a.x0) / (a.x1 - a.x0) * RainMap.COLUMNS).toInt()
            val row = ((ty - a.y0) / (a.y1 - a.y0) * RainMap.ROWS).toInt()
            if (col !in 0 until RainMap.COLUMNS || row !in 0 until RainMap.ROWS) return null
            return a.frames.getOrNull(frameIndex)?.values?.getOrNull(row * RainMap.COLUMNS + col)
        }

        /** Lo que marca el radar en un punto de la pantalla, en la imagen que se ve; null si no se sabe. */
        fun radarAt(point: Offset, w: Float, h: Float): String? {
            val index = radarPosition(radarFrames, timeAt(frames, position.value))?.roundToInt() ?: return null
            val tile = 512f * scale
            val tx = (centerX + (point.x - w / 2) / tile) / radarFactor
            val ty = (centerY + (point.y - h / 2) / tile) / radarFactor
            val slot = radarTiles.indexOf(Tile(floor(tx).toInt(), floor(ty).toInt()))
            val image = radarUrls.getOrNull(index)?.getOrNull(slot)?.let { radarImages[it] } ?: return null
            // El radar da colores, no milímetros: se dice si marca lluvia o no.
            return if (RainMap.radarWet(image, tx - floor(tx), ty - floor(ty))) {
                tr("Lluvia según el radar", "Rain on the radar")
            } else {
                tr("Sin lluvia según el radar", "No rain on the radar")
            }
        }

        val pinReach = with(density) { 22.dp.toPx() }
        // Tocar: dice qué llueve en el punto; si es una de tus ciudades, además centra el mapa en ella.
        val onMapTap: (Offset, Float, Float) -> Unit = { point, w, h ->
            val tile = 512f * scale
            val pin = pins.firstOrNull {
                val x = w / 2 + ((Geo.tileX(it.longitude, zoom) - centerX) * tile).toFloat()
                val y = h / 2 + ((Geo.tileY(it.latitude, zoom) - centerY) * tile).toFloat()
                abs(x - point.x) < pinReach && abs(y - point.y) < pinReach
            }
            // Se lee antes de mover el mapa.
            val text = if (shown.radar) radarAt(point, w, h) else rainAt(point, w, h)?.let { rainText(it) }
            if (pin != null) {
                centerX = Geo.tileX(pin.longitude, zoom)
                centerY = Geo.tileY(pin.latitude, zoom)
                probe = text?.let { Offset(w / 2, h / 2) to it }
            } else {
                probe = text?.let { point to it }
            }
        }
        // Los gestos se instalan una sola vez: así llaman siempre a la versión de esta composición.
        val tapHandler = rememberUpdatedState(onMapTap)

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = tr("Mapa de lluvia", "Rain map") }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        probe = null
                        // Acercar o alejar alrededor de los dedos, y arrastrar.
                        val before = 512f * scale
                        var newScale = scale * gestureZoom
                        val pointX = centerX + (centroid.x - size.width / 2) / before
                        val pointY = centerY + (centroid.y - size.height / 2) / before
                        var cx = pointX - (centroid.x - size.width / 2) / (512f * newScale)
                        var cy = pointY - (centroid.y - size.height / 2) / (512f * newScale)
                        cx -= pan.x / (512f * newScale)
                        cy -= pan.y / (512f * newScale)
                        var z = zoom
                        // Al pasar de cierto tamaño, se cambia de nivel de teselas.
                        if (newScale > 1.5f && z < RainMap.MAX_ZOOM) {
                            z++; newScale /= 2f; cx *= 2; cy *= 2
                        } else if (newScale < 0.75f && z > RainMap.MIN_ZOOM) {
                            z--; newScale *= 2f; cx /= 2; cy /= 2
                        }
                        newScale = newScale.coerceIn(0.75f, if (z == RainMap.MAX_ZOOM) 2f else 1.5f)
                        zoom = z
                        scale = newScale
                        centerX = cx
                        centerY = cy.coerceIn(0.0, (1 shl z).toDouble())
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoom < RainMap.MAX_ZOOM) {
                                zoom++; centerX *= 2; centerY *= 2
                            }
                        },
                        onTap = { point -> tapHandler.value(point, size.width.toFloat(), size.height.toFloat()) },
                        onLongPress = { point ->
                            // Del punto de la pantalla a latitud y longitud (Mercator, al revés).
                            val tile = 512f * scale
                            val world = (1 shl zoom).toDouble()
                            val tx = centerX + (point.x - size.width / 2f) / tile
                            val ty = centerY + (point.y - size.height / 2f) / tile
                            val lon = ((tx / world * 360.0 - 180.0) % 360.0 + 540.0) % 360.0 - 180.0
                            val lat = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * ty / world))))
                            if (onAddPlace != null && lat in -85.0..85.0) {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                probe = null
                                val existing = WeatherRepository.get(context).places().firstOrNull {
                                    abs(it.latitude - lat) < 0.02 && abs(it.longitude - lon) < 0.02
                                }
                                if (existing != null) {
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            tr("${existing.shownName} ya está en tus ciudades", "${existing.shownName} is already in your cities"),
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                } else {
                                    adding = MapPoint(lat, lon, point)
                                }
                            }
                        },
                    )
                },
        ) {
            drawRect(if (dark) Color(0xFF1B1D21) else Color(0xFFE9ECF1))
            val tileSize = IntSize(tilePx.roundToInt(), tilePx.roundToInt())
            fun screen(tx: Double, ty: Double) = Offset(
                (this.size.width / 2 + (tx - centerX) * tilePx).toFloat(),
                (this.size.height / 2 + (ty - centerY) * tilePx).toFloat(),
            )
            tiles.forEach { tile ->
                images[RainMap.baseTileUrl(dark, zoom, Math.floorMod(tile.x, 1 shl zoom), tile.y)]?.let {
                    val p = screen(tile.x.toDouble(), tile.y.toDouble())
                    drawImage(it, dstOffset = IntOffset(p.x.roundToInt(), p.y.roundToInt()), dstSize = tileSize)
                }
            }
            // Hasta la última imagen del radar se dibuja el radar; después, o si no trajo nada, el modelo.
            val radarPos = radarPosition(radarFrames, timeAt(frames, position.value))
            if (radarPos != null) {
                fun radarLayer(index: Int, alpha: Float) {
                    val urls = radarUrls.getOrNull(index) ?: return
                    if (alpha <= 0.01f) return
                    radarTiles.forEachIndexed { slot, tile ->
                        val image = urls.getOrNull(slot)?.let { radarImages[it] } ?: return@forEachIndexed
                        if (RainMap.isBlank(image)) return@forEachIndexed
                        // Las esquinas se redondean por separado: así no quedan rayas entre teselas.
                        val corner = screen(tile.x * radarFactor, tile.y * radarFactor)
                        val opposite = screen((tile.x + 1) * radarFactor, (tile.y + 1) * radarFactor)
                        val left = corner.x.roundToInt()
                        val top = corner.y.roundToInt()
                        val w = opposite.x.roundToInt() - left
                        val h = opposite.y.roundToInt() - top
                        if (w > 0 && h > 0) {
                            drawImage(image, dstOffset = IntOffset(left, top), dstSize = IntSize(w, h), alpha = alpha * 0.85f)
                        }
                    }
                }
                // Entre dos imágenes se mezclan las dos, igual que el modelo entre dos horas.
                val i0 = floor(radarPos).toInt()
                val t = radarPos - i0
                radarLayer(i0, sqrt(1f - t))
                if (t > 0.01f) radarLayer(i0 + 1, sqrt(t))
            } else loaded?.let { a ->
                val topLeft = screen(a.x0 * factor, a.y0 * factor)
                val bottomRight = screen(a.x1 * factor, a.y1 * factor)
                val dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
                val dstSize = IntSize((bottomRight.x - topLeft.x).roundToInt(), (bottomRight.y - topLeft.y).roundToInt())
                fun rainLayer(index: Int, alpha: Float) {
                    val overlay = a.overlays.getOrNull(index) ?: return
                    if (alpha > 0.01f) {
                        drawImage(overlay, dstOffset = dstOffset, dstSize = dstSize, alpha = alpha, filterQuality = FilterQuality.High)
                    }
                }
                // Entre dos horas se mezclan las dos (raíz cuadrada: no se apaga en el medio).
                val pos = position.value.coerceIn(0f, (a.overlays.size - 1).coerceAtLeast(0).toFloat())
                val i0 = floor(pos).toInt()
                val t = pos - i0
                rainLayer(i0, sqrt(1f - t))
                if (t > 0.01f) rainLayer((i0 + 1).coerceAtMost(a.overlays.lastIndex), sqrt(t))
            }

            // Tus ciudades con su nombre, y donde estás con el color de la app.
            pins.forEachIndexed { index, pin ->
                val p = screen(Geo.tileX(pin.longitude, zoom), Geo.tileY(pin.latitude, zoom))
                if (p.x < -40 || p.y < -40 || p.x > this.size.width + 40 || p.y > this.size.height + 40) return@forEachIndexed
                drawCircle(Color.White, radius = (if (pin.here) 9 else 6).dp.toPx(), center = p)
                drawCircle(if (pin.here) markerColor else Color(0xFF5C6370), radius = (if (pin.here) 6 else 4).dp.toPx(), center = p)
                val label = pinLabels.getOrNull(index) ?: return@forEachIndexed
                val at = Offset(p.x + 10.dp.toPx(), p.y - label.size.height / 2f)
                drawText(label, color = pinHalo, topLeft = at, drawStyle = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
                drawText(label, topLeft = at)
            }
            probe?.let { (point, _) ->
                drawCircle(Color.White, radius = 7.dp.toPx(), center = point)
                drawCircle(Color(0xFF1E88E5), radius = 4.dp.toPx(), center = point)
            }
            // El punto que se está por agregar, con el color de la app.
            adding?.let { point ->
                drawCircle(Color.White, radius = 9.dp.toPx(), center = point.screen)
                drawCircle(markerColor, radius = 6.dp.toPx(), center = point.screen)
            }
        }

        Row(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // El lugar del mapa, donde iba la flecha: el mapa es una pestaña, no hay a dónde volver.
            // Vidrio oscuro como la barra de abajo: se lee igual sobre el mapa claro y el oscuro.
            Surface(
                color = SkySurface.bar,
                contentColor = SkySurface.content,
                shape = ClimaShapes.pill,
                border = BorderStroke(1.dp, SkySurface.barBorder),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 12.dp)
                    .height(40.dp),
            ) {
                Row(Modifier.padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(placeName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Vuelve al lugar elegido, con el zoom del principio.
            MapButton(onClick = {
                zoom = 7
                scale = 1f
                centerX = Geo.tileX(longitude, 7)
                centerY = Geo.tileY(latitude, 7)
                probe = null
            }) {
                Icon(Icons.Rounded.LocationOn, contentDescription = tr("Volver al lugar", "Back to the place"))
            }
        }

        // Arriba, debajo de los botones: abajo lo taparía el panel de la línea de tiempo.
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
        )

        // Acercar y alejar flotando al costado, como en los mapas.
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapButton(onClick = {
                if (zoom < RainMap.MAX_ZOOM) {
                    zoom++; centerX *= 2; centerY *= 2
                }
            }) {
                Icon(Icons.Rounded.Add, contentDescription = tr("Acercar", "Zoom in"))
            }
            MapButton(onClick = {
                if (zoom > RainMap.MIN_ZOOM) {
                    zoom--; centerX /= 2; centerY /= 2
                }
            }) {
                MinusGlyph(tr("Alejar", "Zoom out"))
            }
        }

        // Pantalla ancha: una tarjeta flotante abajo al principio y el mapa a todo el ancho; en
        // el teléfono, la hoja de borde a borde que llega hasta abajo, detrás de la barra flotante.
        val wide = maxWidth >= 720.dp
        val barSpace = LocalBottomBarSpace.current
        val sheetModifier = if (wide) {
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp + barSpace)
                .width(380.dp)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        }
        // En la hoja, el contenido queda arriba de la barra de abajo.
        val contentModifier: Modifier = if (wide) Modifier else Modifier.navigationBarsPadding().padding(bottom = barSpace)
        // El mismo vidrio que el pronóstico, pero casi opaco: por detrás pasan los nombres del mapa
        // y los colores del radar, y acá hay letra chica.
        Surface(
            color = MapSheetGlass,
            contentColor = SkySurface.content,
            shape = if (wide) ClimaShapes.card else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            border = BorderStroke(1.dp, SkySurface.border),
            modifier = sheetModifier,
        ) {
            // Lo de adentro pide sus colores al vidrio (surfaceInk), no al tema.
            OnSkyGlass {
                val ink = surfaceInk()
                Column(contentModifier.animateContentSize()) {
                    // Línea fina mientras se baja algo (modelo o radar); el lugar queda reservado
                    // para que la hoja no salte.
                    Box(Modifier.fillMaxWidth().height(3.dp)) {
                        if (loading || radarLoading || (frames.isEmpty() && error == null)) {
                            LinearProgressIndicator(
                                color = ink.accent,
                                trackColor = ink.track,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp)
                                    .height(2.dp)
                                    .semantics { contentDescription = tr("Cargando", "Loading") },
                            )
                        }
                    }
                    val known = shown.epochSeconds >= 0
                    val title = if (known) frameLabel(shown.epochSeconds, zone, minutes = shown.radar) else tr("Mapa de lluvia", "Rain map")
                    // Qué se está viendo: lo que midió el radar, o lo que calcula el modelo (pasado o futuro).
                    val kind = when {
                        shown.radar -> tr("Radar", "Radar")
                        shown.epochSeconds > System.currentTimeMillis() / 1000 -> tr("Pronóstico", "Forecast")
                        else -> tr("Estimación del modelo", "Model estimate")
                    }
                    // Los botones de texto con el celeste del vidrio: el primario del tema claro no se lee acá.
                    val glassButton = ButtonDefaults.textButtonColors(contentColor = ink.accent)
                    Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 7.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                if (known) {
                                    Text(kind, style = MaterialTheme.typography.labelMedium, color = ink.accent)
                                }
                                // La hora que se ve, grande; abajo, lo tocado o el error.
                                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val status = error ?: probe?.second?.let { tr("Acá: ", "Here: ") + it }
                                    ?: if (frames.isEmpty()) tr("Cargando…", "Loading…") else null
                                if (status != null) {
                                    Text(status, style = MaterialTheme.typography.bodyMedium, color = ink.variant)
                                }
                            }
                            if (error != null) {
                                TextButton(onClick = { attempt++ }, colors = glassButton) { Text(tr("Reintentar", "Retry")) }
                            }
                            // "Ahora" solo si la línea de tiempo no está ya ahí.
                            if (frames.size > 1 && !atNow) {
                                TextButton(onClick = { scrub(nowPosition) }, colors = glassButton) { Text(tr("Ahora", "Now")) }
                            }
                            // La leyenda y las fuentes, a pedido: la hoja tapa menos mapa.
                            IconButton(onClick = { showInfo = !showInfo }) {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = if (showInfo) {
                                        tr("Ocultar la leyenda y las fuentes", "Hide legend and sources")
                                    } else {
                                        tr("Ver la leyenda y las fuentes", "Show legend and sources")
                                    },
                                    tint = if (showInfo) ink.accent else ink.variant,
                                )
                            }
                        }
                        if (frames.size > 1) {
                            Spacer(Modifier.height(6.dp))
                            Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FilledTonalIconButton(
                                    onClick = { playing = !playing },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = ink.track, contentColor = ink.content),
                                ) {
                                    if (playing) {
                                        PauseGlyph(tr("Pausar", "Pause"))
                                    } else {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = tr("Reproducir", "Play"))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                // Lluvia en el centro del mapa, hora por hora: se ve de un vistazo cuándo llueve.
                                val centerRain = remember(area, zoom, (centerX * 4).roundToInt(), (centerY * 4).roundToInt()) {
                                    area?.let { a ->
                                        val f = 2.0.pow(zoom - a.zoom)
                                        val col = ((centerX / f - a.x0) / (a.x1 - a.x0) * RainMap.COLUMNS).toInt().coerceIn(0, RainMap.COLUMNS - 1)
                                        val row = ((centerY / f - a.y0) / (a.y1 - a.y0) * RainMap.ROWS).toInt().coerceIn(0, RainMap.ROWS - 1)
                                        a.frames.map { it.values.getOrElse(row * RainMap.COLUMNS + col) { 0f } }
                                    }.orEmpty()
                                }
                                // El tramo de la línea de tiempo que cubre el radar, si se está usando.
                                val radarSpan = if (radarFrames.isEmpty()) {
                                    null
                                } else {
                                    positionAt(frames, radarFrames.first().epochSeconds)..positionAt(frames, radarFrames.last().epochSeconds)
                                }
                                RainTimeline(
                                    frames = frames,
                                    rain = centerRain,
                                    position = { position.value },
                                    current = frameIndex,
                                    stateText = if (known) "$kind, $title" else title,
                                    radarSpan = radarSpan,
                                    zone = zone,
                                    onScrub = scrub,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (showInfo) {
                            Column(Modifier.padding(end = 12.dp)) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (onAddPlace != null) {
                                        tr("Tocá el mapa para ver si llueve en un punto; mantené apretado para agregar una ciudad.", "Tap the map to see if it rains at a point; long-press to add a city.")
                                    } else {
                                        tr("Tocá el mapa para ver si llueve en un punto.", "Tap the map to see if it rains at a point.")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ink.variant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    tr(
                                        "Hasta ahora se ve lo que midió el radar en las últimas 2 horas. De ahí en adelante, y donde el radar no muestra nada, se ve lo que calcula el modelo del pronóstico.",
                                        "Up to now you see what the radar measured over the last 2 hours. From there on, and where the radar shows nothing, you see what the forecast model calculates.",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ink.variant,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    tr("Colores del pronóstico (el radar usa su propia escala)", "Forecast colors (the radar uses its own scale)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ink.variant,
                                )
                                Spacer(Modifier.height(4.dp))
                                RainLegend()
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    tr(
                                        "Radar: RainViewer · Pronóstico: Open-Meteo · Mapa: Esri, HERE, Garmin, © OpenStreetMap",
                                        "Radar: RainViewer · Forecast: Open-Meteo · Map: Esri, HERE, Garmin, © OpenStreetMap",
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ink.variant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** El zoom con que se pide el radar: el del mapa, hasta el máximo gratuito, o menos si no entra en el tope. */
private fun radarZoomFor(zoom: Int, widthPx: Float, heightPx: Float, images: Int): Int {
    var z = minOf(zoom, RainMap.RADAR_MAX_ZOOM)
    while (z > 0 && radarTileCount(widthPx, heightPx, zoom - z) * images > RADAR_TILE_BUDGET) z--
    return z
}

/** Cuántas teselas del radar puede hacer falta para cubrir la pantalla si cada una se estira [levels] niveles. */
private fun radarTileCount(widthPx: Float, heightPx: Float, levels: Int): Int {
    val side = 512f * (1 shl levels.coerceIn(0, 12))
    return (ceil(widthPx / side).toInt() + 1) * (ceil(heightPx / side).toInt() + 1)
}

/** La hora (epoch) de una posición continua de la línea de tiempo; -1 si no hay horas. */
private fun timeAt(frames: List<RainFrame>, position: Float): Long {
    if (frames.isEmpty()) return -1L
    val p = position.coerceIn(0f, frames.lastIndex.toFloat())
    val i = floor(p).toInt()
    val a = frames[i].epochSeconds
    val b = frames[(i + 1).coerceAtMost(frames.lastIndex)].epochSeconds
    return a + ((b - a) * (p - i)).toLong()
}

/** La posición de la línea de tiempo que corresponde a una hora, sin salirse de los extremos. */
private fun positionAt(frames: List<RainFrame>, time: Long): Float {
    if (frames.size < 2 || time <= frames.first().epochSeconds) return 0f
    val next = frames.indexOfFirst { it.epochSeconds > time }
    if (next < 1) return frames.lastIndex.toFloat()
    val a = frames[next - 1].epochSeconds
    val b = frames[next].epochSeconds
    return (next - 1) + (time - a).toFloat() / (b - a).coerceAtLeast(1L).toFloat()
}

/**
 * Dónde arranca la línea de tiempo: en la hora guardada ([savedTime]) si sigue entre las cargadas;
 * si no, en la última imagen del radar ([radarTime]) o en la hora en curso.
 */
private fun startPosition(frames: List<RainFrame>, savedTime: Long, radarTime: Long?): Float {
    if (frames.isEmpty()) return 0f
    val range = frames.first().epochSeconds..frames.last().epochSeconds
    if (savedTime in range) return positionAt(frames, savedTime)
    if (radarTime != null && radarTime in range) return positionAt(frames, radarTime)
    val now = System.currentTimeMillis() / 1000
    return frames.indexOfLast { it.epochSeconds <= now }.coerceAtLeast(0).toFloat()
}

/**
 * Posición continua entre las imágenes del radar para una hora (1,5 es "entre la segunda y la
 * tercera"), o null si esa hora no la cubre el radar: es más vieja que la primera o es futuro.
 */
private fun radarPosition(images: List<RadarImage>, time: Long): Float? {
    if (images.isEmpty() || time < 0) return null
    if (time < images.first().epochSeconds - RADAR_SLACK_SECONDS) return null
    if (time > images.last().epochSeconds + RADAR_SLACK_SECONDS) return null
    val next = images.indexOfFirst { it.epochSeconds > time }
    if (next < 0) return images.lastIndex.toFloat()
    if (next == 0) return 0f
    val a = images[next - 1].epochSeconds
    val b = images[next].epochSeconds
    return (next - 1) + (time - a).toFloat() / (b - a).coerceAtLeast(1L).toFloat()
}

/**
 * La hoja de la línea de tiempo: el tono del vidrio del cielo, casi opaco. Con la opacidad de las
 * tarjetas del pronóstico las etiquetas chicas se mezclaban con los nombres del mapa claro.
 */
private val MapSheetGlass = SkySurface.Tint.copy(alpha = 0.86f)

/** Botón redondo flotando sobre el mapa (volver al lugar, acercar, alejar), del vidrio de la barra. */
@Composable
private fun MapButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    // Surface con onClick ya se anuncia como botón y reserva 48 dp de toque alrededor de los 40 que se ven.
    Surface(
        onClick = onClick,
        color = SkySurface.bar,
        contentColor = SkySurface.content,
        shape = ClimaShapes.pill,
        border = BorderStroke(1.dp, SkySurface.barBorder),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { content() }
    }
}

/** El "−" de alejar, dibujado: el juego de íconos del proyecto no trae uno. */
@Composable
private fun MinusGlyph(description: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val y = size.height / 2
        drawLine(color, Offset(size.width * 0.23f, y), Offset(size.width * 0.77f, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Las dos barras de pausa, dibujadas, del mismo tamaño que el ícono de reproducir. */
@Composable
private fun PauseGlyph(description: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp).semantics { contentDescription = description }) {
        val bar = Size(size.width / 6f, size.height * 0.5f)
        val top = (size.height - bar.height) / 2
        val radius = CornerRadius(bar.width / 3)
        drawRoundRect(color, topLeft = Offset(size.width / 2 - bar.width * 1.5f, top), size = bar, cornerRadius = radius)
        drawRoundRect(color, topLeft = Offset(size.width / 2 + bar.width * 0.5f, top), size = bar, cornerRadius = radius)
    }
}

/**
 * Línea de tiempo propia: una barra por hora con la lluvia en el centro del mapa (del color de su
 * intensidad), una marca en la hora actual y el cursor que se arrastra o se toca. El cursor se
 * mueve parejo, sin saltos de hora en hora. Si se está usando el radar, abajo se marca qué tramo
 * es radar ([radarSpan], en posiciones) y dónde empieza el pronóstico.
 */
@Composable
private fun RainTimeline(
    frames: List<RainFrame>,
    rain: List<Float>,
    position: () -> Float,
    current: Int,
    stateText: String,
    radarSpan: ClosedFloatingPointRange<Float>?,
    zone: ZoneId,
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Los colores de donde esté dibujada (acá, el vidrio de la hoja).
    val ink = surfaceInk()
    val nowIndex = frames.indexOfLast { it.epochSeconds <= System.currentTimeMillis() / 1000 }
    val last = frames.lastIndex.toFloat()
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                // Para TalkBack es un control deslizante: una hora por paso.
                .semantics {
                    contentDescription = tr("Línea de tiempo de la lluvia", "Rain timeline")
                    stateDescription = stateText
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current.toFloat().coerceIn(0f, last.coerceAtLeast(0f)),
                        0f..last.coerceAtLeast(0f),
                        (frames.size - 2).coerceAtLeast(0),
                    )
                    setProgress(action = { value ->
                        onScrub(value.roundToInt().toFloat())
                        true
                    })
                }
                .pointerInput(frames.size) {
                    detectTapGestures { onScrub(it.x / size.width * frames.size - 0.5f) }
                }
                .pointerInput(frames.size) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        onScrub((change.position.x / size.width * frames.size - 0.5f).coerceIn(0f, last))
                    }
                },
        ) {
            val slot = size.width / frames.size
            val barWidth = slot * 0.56f
            val maxBar = size.height - 10.dp.toPx()
            frames.indices.forEach { i ->
                val mm = rain.getOrElse(i) { 0f }
                val h = if (mm < 0.1f) 3.dp.toPx() else (maxBar * (mm / 6f).coerceIn(0.18f, 1f))
                val color = if (mm < 0.1f) ink.track else Color(rainColorOf(mm))
                // Las horas que ya pasaron, más tenues.
                val alpha = if (nowIndex >= 0 && i < nowIndex) 0.55f else 1f
                drawRoundRect(
                    color.copy(alpha = color.alpha * alpha),
                    topLeft = Offset(slot * i + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
            if (nowIndex >= 0) {
                val x = slot * nowIndex + slot / 2
                drawLine(ink.variant.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            // Cursor: se lee la posición al dibujar, así moverse no recompone nada.
            val x = slot * position() + slot / 2
            // Del color del texto y no del acento: el celeste se confundía con las barras de llovizna.
            drawLine(ink.content, Offset(x, 4.dp.toPx()), Offset(x, size.height), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(ink.content, radius = 6.dp.toPx(), center = Offset(x, 6.dp.toPx()))
            drawCircle(ink.knockout, radius = 2.5.dp.toPx(), center = Offset(x, 6.dp.toPx()))
        }
        // Horas cada 3 y "Ahora" en su lugar.
        Row(Modifier.fillMaxWidth()) {
            frames.indices.forEach { i ->
                val label = when {
                    i == nowIndex -> tr("Ahora", "Now")
                    i % 3 == 0 && abs(i - nowIndex) > 1 -> timeOf(frames[i].epochSeconds, zone)
                    else -> ""
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == nowIndex) ink.accent else ink.variant,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true),
                )
            }
        }
        // Dónde termina lo medido y empieza lo calculado: "Radar | Pronóstico".
        if (radarSpan != null) {
            val measurer = rememberTextMeasurer()
            val style = MaterialTheme.typography.labelSmall
            val radarWord = remember(style, ink.accent) {
                measurer.measure(tr("Radar", "Radar"), style.copy(color = ink.accent))
            }
            val forecastWord = remember(style, ink.variant) {
                measurer.measure(tr("Pronóstico", "Forecast"), style.copy(color = ink.variant))
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .height(22.dp),
            ) {
                val slot = size.width / frames.size
                val start = slot * radarSpan.start + slot / 2
                val end = slot * radarSpan.endInclusive + slot / 2
                val y = 2.dp.toPx()
                drawLine(ink.track, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(ink.accent, Offset(start, y), Offset(end, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawLine(ink.variant, Offset(end, 0f), Offset(end, 8.dp.toPx()), strokeWidth = 1.dp.toPx())
                val gap = 5.dp.toPx()
                val top = 5.dp.toPx()
                // "Radar" termina en el corte y "Pronóstico" empieza ahí, sin salirse de la línea.
                drawText(radarWord, topLeft = Offset((end - gap - radarWord.size.width).coerceAtLeast(0f), top))
                drawText(forecastWord, topLeft = Offset((end + gap).coerceAtMost(size.width - forecastWord.size.width).coerceAtLeast(0f), top))
            }
        }
    }
}

/** Mismo color que la lluvia del mapa, opaco, para las barras de la línea de tiempo. */
private fun rainColorOf(mm: Float): Long = when {
    mm < 0.5f -> 0xFF81D4FA
    mm < 1.5f -> 0xFF42A5F5
    mm < 4f -> 0xFF1E88E5
    mm < 8f -> 0xFFFFC107
    else -> 0xFFE53935
}

/** Qué colores son qué lluvia, en mm por hora. */
@Composable
private fun RainLegend() {
    val steps = listOf(
        Color(0xFF81D4FA) to tr("Llovizna", "Drizzle"),
        Color(0xFF42A5F5) to tr("Débil", "Light"),
        Color(0xFF1E88E5) to tr("Moderada", "Moderate"),
        Color(0xFFFFC107) to tr("Fuerte", "Heavy"),
        Color(0xFFE53935) to tr("Muy fuerte", "Very heavy"),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEach { (color, label) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(ClimaShapes.pill)
                        .background(color),
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = surfaceInk().variant)
            }
        }
    }
}

/** "Lluvia moderada · 2,1 mm" para el punto tocado. */
private fun rainText(mm: Float): String {
    // En la unidad elegida (mm o pulgadas).
    val amount = mmText(mm.toDouble())
    return when {
        mm < 0.1f -> tr("Sin lluvia", "No rain")
        mm < 0.5f -> tr("Llovizna", "Drizzle") + " · $amount"
        mm < 1.5f -> tr("Lluvia débil", "Light rain") + " · $amount"
        mm < 4f -> tr("Lluvia moderada", "Moderate rain") + " · $amount"
        mm < 8f -> tr("Lluvia fuerte", "Heavy rain") + " · $amount"
        else -> tr("Lluvia muy fuerte", "Very heavy rain") + " · $amount"
    }
}

private fun timeOf(epochSeconds: Long, zone: ZoneId): String =
    shortHour(Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDateTime())

/** "14:20 · hace 20 min". [minutes]: para el radar, que va de a 10 minutos; el modelo va por hora. */
private fun frameLabel(epochSeconds: Long, zone: ZoneId, minutes: Boolean): String {
    val seconds = epochSeconds - System.currentTimeMillis() / 1000
    val mins = (seconds / 60.0).roundToInt()
    val hours = (seconds / 3600.0).roundToInt()
    val relative = when {
        minutes && abs(mins) < 3 -> tr("ahora", "now")
        minutes && abs(mins) < 60 -> if (mins < 0) tr("hace ${-mins} min", "${-mins} min ago") else tr("en $mins min", "in $mins min")
        hours == 0 -> tr("ahora", "now")
        hours < 0 -> tr("hace ${-hours} h", "${-hours} h ago")
        else -> tr("en $hours h", "in $hours h")
    }
    return "${timeOf(epochSeconds, zone)} · $relative"
}

/**
 * "¿Agregar Córdoba a tus ciudades?". Mientras el geocodificador busca el nombre se dice que se
 * está buscando; si el teléfono no tiene o no encuentra nada, se ofrece con las coordenadas.
 */
@Composable
private fun AddPlaceDialog(point: MapPoint, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val context = LocalContext.current
    val fallback = String.format(java.util.Locale.getDefault(), "%.2f, %.2f", point.latitude, point.longitude)
    // null mientras busca; después, el nombre o las coordenadas.
    val name by produceState<String?>(null, point) {
        value = LocationProvider.placeName(context, Coordinates(point.latitude, point.longitude)) ?: fallback
    }
    val shown = name
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Place, contentDescription = null) },
        title = {
            Text(
                if (shown == null) tr("Buscando el lugar…", "Looking up the place…") else tr("¿Agregar $shown a tus ciudades?", "Add $shown to your cities?"),
            )
        },
        text = {
            Text(
                tr(
                    "Vas a ver su pronóstico al deslizar entre ciudades, en los widgets y en los avisos.",
                    "You'll see its forecast when swiping between cities, in widgets and in alerts.",
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (shown != null) onConfirm(shown) }, enabled = shown != null) {
                Text(tr("Agregar", "Add"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}
