package com.mnavarrosqa.clima.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Geo
import com.mnavarrosqa.clima.data.RainFrame
import com.mnavarrosqa.clima.data.RainMap
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

private data class Tile(val x: Int, val y: Int)

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

/**
 * Mapa de lluvia animado (3 horas atrás y 12 adelante). Se arrastra, se pellizca para acercar,
 * doble toque acerca y tocar un punto dice cuánto llueve ahí a esa hora. Muestra tus ciudades.
 */
@Composable
fun RadarScreen(latitude: Double, longitude: Double, onBack: () -> Unit) {
    // El tema elegido en la app (claro u oscuro), no el del sistema.
    val dark = LocalDarkTheme.current
    val context = LocalContext.current
    // Vista: zoom entero de las teselas, escala del pellizco (0,75 a 1,5) y centro en teselas.
    var zoom by rememberSaveable { mutableIntStateOf(7) }
    var scale by remember { mutableFloatStateOf(1f) }
    var centerX by rememberSaveable { mutableDoubleStateOf(Geo.tileX(longitude, 7)) }
    var centerY by rememberSaveable { mutableDoubleStateOf(Geo.tileY(latitude, 7)) }
    var area by remember { mutableStateOf<RainArea?>(null) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    // Sube para volver a intentar después de un error.
    var attempt by remember { mutableIntStateOf(0) }
    // Lo que dice el punto tocado: "Lluvia moderada · 2,1 mm".
    var probe by remember { mutableStateOf<Pair<Offset, String>?>(null) }
    // Las horas del mapa en la zona del lugar que se mira, no en la del teléfono.
    val cached = remember { WeatherRepository.get(context).allCached() }
    val zone = remember(latitude, longitude) {
        cached.firstOrNull { abs(it.latitude - latitude) < 0.01 && abs(it.longitude - longitude) < 0.01 }
            ?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
    }
    val pins = remember {
        cached.map { MapPin(it.place ?: tr("Mi ubicación", "My location"), it.latitude, it.longitude, it.placeId == CURRENT_PLACE_ID) }
    }
    val images = remember { mutableStateMapOf<String, ImageBitmap>() }
    val frames = area?.frames.orEmpty()

    // Fundido entre horas para que la lluvia se mueva sin saltos.
    var previousIndex by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(frameIndex) {
        fade.snapTo(0f)
        fade.animateTo(1f, tween(500, easing = LinearEasing))
        previousIndex = frameIndex
    }
    LaunchedEffect(playing, frames.size) {
        while (playing && frames.isNotEmpty()) {
            delay(if (frameIndex == frames.lastIndex) 1600 else 800)
            frameIndex = (frameIndex + 1) % frames.size
        }
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

        LaunchedEffect(tiles, dark) {
            coroutineScope {
                tiles.map { tile ->
                    val url = RainMap.baseTileUrl(dark, zoom, Math.floorMod(tile.x, 1 shl zoom), tile.y)
                    async { if (url !in images) RainMap.tile(url)?.let { images[url] = it } }
                }.awaitAll()
            }
            // Al moverse quedan teselas viejas: se descartan para no acumular memoria.
            if (images.size > 160) {
                val keep = tiles.map { RainMap.baseTileUrl(dark, zoom, Math.floorMod(it.x, 1 shl zoom), it.y) }.toSet()
                images.keys.retainAll(keep)
            }
        }

        // La lluvia se pide para lo visible con un margen; se vuelve a pedir recién cuando lo que
        // se ve sale de lo cargado o cambia el zoom (con una pausa, para no pedir en cada arrastre).
        val loaded = area
        val factor = loaded?.let { 2.0.pow(zoom - it.zoom) } ?: 1.0
        val covered = loaded != null && loaded.zoom == zoom &&
            centerX - halfW >= loaded.x0 && centerX + halfW <= loaded.x1 &&
            centerY - halfH >= loaded.y0 && centerY + halfH <= loaded.y1
        val wantKey = if (covered) null else Triple(zoom, (centerX * 2).roundToInt(), (centerY * 2).roundToInt())
        LaunchedEffect(wantKey, attempt) {
            if (wantKey == null && attempt == 0 && loaded != null) return@LaunchedEffect
            if (loaded != null) delay(350)
            error = null
            loading = true
            val x0 = centerX - halfW * 1.6
            val x1 = centerX + halfW * 1.6
            val y0 = (centerY - halfH * 1.4).coerceAtLeast(0.0)
            val y1 = (centerY + halfH * 1.4).coerceAtMost(worldSize)
            val requestZoom = zoom
            runCatching { RainMap.load(x0, y0, x1, y1, requestZoom) }
                .onSuccess { result ->
                    val first = area == null
                    area = RainArea(x0, y0, x1, y1, requestZoom, result, result.map(RainMap::overlay))
                    if (first || frameIndex > result.lastIndex) {
                        // Arranca en la hora actual.
                        val now = System.currentTimeMillis() / 1000
                        frameIndex = result.indexOfLast { it.epochSeconds <= now }.coerceAtLeast(0)
                        previousIndex = frameIndex
                    }
                }
                .onFailure { failure ->
                    // Si se canceló porque se movió el mapa, no es un error: lo carga el efecto nuevo.
                    if (failure is kotlinx.coroutines.CancellationException) throw failure
                    error = tr("No pude cargar el mapa de lluvia. Revisá la conexión.", "Couldn't load the rain map. Check your connection.")
                }
            loading = false
        }

        val frame = frames.getOrNull(frameIndex)
        val markerColor = MaterialTheme.colorScheme.primary
        val measurer = rememberTextMeasurer()
        val pinStyle = TextStyle(
            color = if (dark) Color.White else Color(0xFF1B1D21),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

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
                        onTap = { point ->
                            val mm = rainAt(point, size.width.toFloat(), size.height.toFloat())
                            probe = if (mm == null) null else point to rainText(mm)
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
            loaded?.let { a ->
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
                if (previousIndex != frameIndex) rainLayer(previousIndex, 1f - fade.value)
                rainLayer(frameIndex, fade.value)
            }

            // Tus ciudades con su nombre, y donde estás con el color de la app.
            pins.forEach { pin ->
                val p = screen(Geo.tileX(pin.longitude, zoom), Geo.tileY(pin.latitude, zoom))
                if (p.x < -40 || p.y < -40 || p.x > this.size.width + 40 || p.y > this.size.height + 40) return@forEach
                drawCircle(Color.White, radius = (if (pin.here) 9 else 6).dp.toPx(), center = p)
                drawCircle(if (pin.here) markerColor else Color(0xFF5C6370), radius = (if (pin.here) 6 else 4).dp.toPx(), center = p)
                val label = measurer.measure(pin.name, pinStyle)
                drawText(label, topLeft = Offset(p.x + 10.dp.toPx(), p.y - label.size.height / 2f))
            }
            probe?.let { (point, _) ->
                drawCircle(Color.White, radius = 7.dp.toPx(), center = point)
                drawCircle(Color(0xFF1E88E5), radius = 4.dp.toPx(), center = point)
            }
        }

        Row(
            Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
            }
            Spacer(Modifier.weight(1f))
            // Vuelve al lugar elegido, con el zoom del principio.
            FilledTonalIconButton(onClick = {
                zoom = 7
                scale = 1f
                centerX = Geo.tileX(longitude, 7)
                centerY = Geo.tileY(latitude, 7)
                probe = null
            }) {
                Icon(Icons.Rounded.LocationOn, contentDescription = tr("Volver al lugar", "Back to the place"))
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 720.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Mapa de lluvia", "Rain map"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            error ?: probe?.second?.let { tr("Acá: ", "Here: ") + it }
                                ?: frame?.let { frameLabel(it, zone) }
                                ?: tr("Cargando…", "Loading…"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (error != null) {
                        TextButton(onClick = { attempt++ }) { Text(tr("Reintentar", "Retry")) }
                    } else if (loading && area != null) {
                        Text(tr("Actualizando…", "Updating…"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                    }
                    FilledTonalButton(
                        onClick = {
                            if (zoom > RainMap.MIN_ZOOM) {
                                zoom--; centerX /= 2; centerY /= 2
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = tr("Alejar", "Zoom out") },
                    ) { Text("−") }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalIconButton(onClick = {
                        if (zoom < RainMap.MAX_ZOOM) {
                            zoom++; centerX *= 2; centerY *= 2
                        }
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = tr("Acercar", "Zoom in"))
                    }
                }
                if (frames.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalIconButton(onClick = { playing = !playing }) {
                            if (playing) {
                                Text("❚❚", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { contentDescription = tr("Pausar", "Pause") })
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = tr("Reproducir", "Play"))
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Slider(
                                value = frameIndex.toFloat(),
                                onValueChange = {
                                    playing = false
                                    probe = null
                                    frameIndex = it.roundToInt()
                                },
                                valueRange = 0f..frames.lastIndex.toFloat(),
                                steps = (frames.size - 2).coerceAtLeast(0),
                            )
                            // Comienzo, ahora y final de la línea de tiempo.
                            Row(Modifier.fillMaxWidth()) {
                                Text(timeOf(frames.first(), zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                Text(timeOf(frames.last(), zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = {
                            playing = false
                            val now = System.currentTimeMillis() / 1000
                            frameIndex = frames.indexOfLast { it.epochSeconds <= now }.coerceAtLeast(0)
                        }) { Text(tr("Ahora", "Now")) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                RainLegend()
                Spacer(Modifier.height(8.dp))
                Text(
                    tr(
                        "Lluvia calculada por Open-Meteo (pronóstico, no radar) · Mapa: Esri, HERE, Garmin, © OpenStreetMap",
                        "Rain modeled by Open-Meteo (forecast, not radar) · Map: Esri, HERE, Garmin, © OpenStreetMap",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
                        .clip(RoundedCornerShape(3.dp))
                        .background(color),
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Lluvia moderada · 2,1 mm" para el punto tocado. */
private fun rainText(mm: Float): String {
    val amount = String.format(java.util.Locale.getDefault(), "%.1f mm", mm)
    return when {
        mm < 0.1f -> tr("Sin lluvia", "No rain")
        mm < 0.5f -> tr("Llovizna", "Drizzle") + " · $amount"
        mm < 1.5f -> tr("Lluvia débil", "Light rain") + " · $amount"
        mm < 4f -> tr("Lluvia moderada", "Moderate rain") + " · $amount"
        mm < 8f -> tr("Lluvia fuerte", "Heavy rain") + " · $amount"
        else -> tr("Lluvia muy fuerte", "Very heavy rain") + " · $amount"
    }
}

private fun timeOf(frame: RainFrame, zone: ZoneId): String =
    shortHour(Instant.ofEpochSecond(frame.epochSeconds).atZone(zone).toLocalDateTime())

private fun frameLabel(frame: RainFrame, zone: ZoneId): String {
    val hours = ((frame.epochSeconds - System.currentTimeMillis() / 1000) / 3600.0).roundToInt()
    val relative = when {
        hours == 0 -> tr("ahora", "now")
        hours < 0 -> tr("hace ${-hours} h", "${-hours} h ago")
        else -> tr("en $hours h", "in $hours h")
    }
    return "${timeOf(frame, zone)} · $relative"
}
