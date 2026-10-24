package com.mnavarrosqa.clima.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.Geo
import com.mnavarrosqa.clima.data.RainFrame
import com.mnavarrosqa.clima.data.RainMap
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private data class Tile(val x: Int, val y: Int)

/** Mapa de lluvia animado (unas horas atrás y adelante) centrado en el lugar elegido. */
@Composable
fun RadarScreen(latitude: Double, longitude: Double, onBack: () -> Unit) {
    val dark = isSystemInDarkTheme()
    var zoom by rememberSaveable { mutableIntStateOf(7) }
    var frames by remember { mutableStateOf<List<RainFrame>>(emptyList()) }
    var overlays by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val images = remember { mutableStateMapOf<String, ImageBitmap>() }

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
        val tilePx = 512f
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centerX = Geo.tileX(longitude, zoom)
        val centerY = Geo.tileY(latitude, zoom)
        val halfW = widthPx / 2 / tilePx
        val halfH = heightPx / 2 / tilePx
        val tiles = remember(zoom, widthPx, heightPx) {
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
            // Al cambiar de zoom quedan teselas viejas: se descartan para no acumular memoria.
            if (images.size > 120) {
                val keep = tiles.map { RainMap.baseTileUrl(dark, zoom, Math.floorMod(it.x, 1 shl zoom), it.y) }.toSet()
                images.keys.retainAll(keep)
            }
        }
        // La lluvia cubre lo visible, con un margen para que no se vea el borde.
        val x0 = centerX - halfW - 0.25
        val x1 = centerX + halfW + 0.25
        val y0 = centerY - halfH - 0.25
        val y1 = centerY + halfH + 0.25
        LaunchedEffect(zoom, widthPx, heightPx) {
            error = null
            runCatching { RainMap.load(x0, y0, x1, y1, zoom) }
                .onSuccess { loaded ->
                    frames = loaded
                    overlays = loaded.map(RainMap::overlay)
                    // Arranca en la hora actual.
                    val now = System.currentTimeMillis() / 1000
                    frameIndex = loaded.indexOfLast { it.epochSeconds <= now }.coerceAtLeast(0)
                    previousIndex = frameIndex
                }
                .onFailure { error = tr("No pude cargar el mapa de lluvia. Revisá la conexión.", "Couldn't load the rain map. Check your connection.") }
        }

        val frame = frames.getOrNull(frameIndex)
        val markerColor = MaterialTheme.colorScheme.primary
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = tr("Mapa de lluvia", "Rain map") },
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
            val topLeft = screen(x0, y0)
            val bottomRight = screen(x1, y1)
            val dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
            val dstSize = IntSize((bottomRight.x - topLeft.x).roundToInt(), (bottomRight.y - topLeft.y).roundToInt())
            fun rainLayer(index: Int, alpha: Float) {
                val overlay = overlays.getOrNull(index) ?: return
                if (alpha > 0.01f) {
                    drawImage(overlay, dstOffset = dstOffset, dstSize = dstSize, alpha = alpha, filterQuality = FilterQuality.High)
                }
            }
            if (previousIndex != frameIndex) rainLayer(previousIndex, 1f - fade.value)
            rainLayer(frameIndex, fade.value)

            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = center)
            drawCircle(markerColor, radius = 6.dp.toPx(), center = center)
        }

        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
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
                            error ?: frame?.let { frameLabel(it) } ?: tr("Cargando…", "Loading…"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { if (zoom > RainMap.MIN_ZOOM) zoom-- }) { Text("−") }
                    FilledTonalIconButton(onClick = { if (zoom < RainMap.MAX_ZOOM) zoom++ }) {
                        Icon(Icons.Rounded.Add, contentDescription = tr("Acercar", "Zoom in"))
                    }
                }
                if (frames.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { playing = !playing }) {
                            if (playing) {
                                Text(tr("Pausa", "Pause"))
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text(tr("Animar", "Play"))
                            }
                        }
                        Slider(
                            value = frameIndex.toFloat(),
                            onValueChange = {
                                playing = false
                                frameIndex = it.roundToInt()
                            },
                            valueRange = 0f..frames.lastIndex.toFloat(),
                            steps = (frames.size - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
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

private fun frameLabel(frame: RainFrame): String {
    val time = LocalDateTime.ofInstant(Instant.ofEpochSecond(frame.epochSeconds), ZoneId.systemDefault())
    val hours = ((frame.epochSeconds - System.currentTimeMillis() / 1000) / 3600.0).roundToInt()
    val relative = when {
        hours == 0 -> tr("ahora", "now")
        hours < 0 -> tr("hace ${-hours} h", "${-hours} h ago")
        else -> tr("en $hours h", "in $hours h")
    }
    return "${shortHour(time)} · $relative"
}
