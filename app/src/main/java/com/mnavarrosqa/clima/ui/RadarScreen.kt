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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.Geo
import com.mnavarrosqa.clima.data.Radar
import com.mnavarrosqa.clima.data.RadarFrame
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private data class Tile(val x: Int, val y: Int)

/** Radar de lluvia animado de las últimas horas centrado en el lugar elegido. */
@Composable
fun RadarScreen(latitude: Double, longitude: Double, onBack: () -> Unit) {
    val dark = isSystemInDarkTheme()
    var zoom by rememberSaveable { mutableIntStateOf(6) }
    var host by remember { mutableStateOf<String?>(null) }
    var frames by remember { mutableStateOf<List<RadarFrame>>(emptyList()) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val images = remember { mutableStateMapOf<String, ImageBitmap>() }

    LaunchedEffect(Unit) {
        runCatching { Radar.frames() }
            .onSuccess { (h, f) ->
                host = h
                frames = f
                // Arranca en el último cuadro observado (antes de los pronosticados).
                frameIndex = (f.indexOfLast { it.time * 1000 <= System.currentTimeMillis() }).coerceAtLeast(0)
            }
            .onFailure { error = "No pude cargar el radar. Revisá la conexión." }
    }
    // Fundido entre cuadros para que la lluvia se mueva sin saltos.
    var previousIndex by remember { mutableIntStateOf(0) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(frameIndex) {
        fade.snapTo(0f)
        fade.animateTo(1f, tween(450, easing = LinearEasing))
        previousIndex = frameIndex
    }
    LaunchedEffect(playing, frames.size) {
        while (playing && frames.isNotEmpty()) {
            delay(if (frameIndex == frames.lastIndex) 1500 else 600)
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
        val tiles = remember(zoom, widthPx, heightPx) {
            val halfW = widthPx / 2 / tilePx
            val halfH = heightPx / 2 / tilePx
            val max = (1 shl zoom) - 1
            buildList {
                for (x in floor(centerX - halfW).toInt()..ceil(centerX + halfW).toInt()) {
                    for (y in floor(centerY - halfH).toInt()..ceil(centerY + halfH).toInt()) {
                        if (y in 0..max) add(Tile(Math.floorMod(x, max + 1), y))
                    }
                }
            }
        }

        // Primero el mapa y el cuadro visible; después el resto de los cuadros para la animación.
        LaunchedEffect(tiles, host, frames, dark) {
            suspend fun load(urls: List<String>) = coroutineScope {
                urls.filter { it !in images }.map { url ->
                    async { Radar.tile(url)?.let { images[url] = it } }
                }.awaitAll()
                // Al cambiar de zoom quedan teselas viejas: se descartan para no acumular memoria.
                if (images.size > 400) {
                    val keep = tiles.flatMap { t ->
                        listOf(Radar.baseTileUrl(dark, zoom, t.x, t.y)) +
                            frames.map { f -> Radar.radarTileUrl(host.orEmpty(), f, zoom, t.x, t.y) }
                    }.toSet()
                    images.keys.retainAll(keep)
                }
            }
            load(tiles.map { Radar.baseTileUrl(dark, zoom, it.x, it.y) })
            val h = host ?: return@LaunchedEffect
            val order = frames.indices.sortedBy { Math.floorMod(it - frameIndex, frames.size.coerceAtLeast(1)) }
            for (i in order) load(tiles.map { Radar.radarTileUrl(h, frames[i], zoom, it.x, it.y) })
        }

        val frame = frames.getOrNull(frameIndex)
        val markerColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            drawRect(if (dark) Color(0xFF1B1D21) else Color(0xFFE9ECF1))
            val size = IntSize(tilePx.roundToInt(), tilePx.roundToInt())
            fun position(tile: Tile): IntOffset {
                // Se usa la x sin envolver para ubicar la tesela respecto del centro.
                var dx = tile.x - centerX
                val world = (1 shl zoom).toDouble()
                if (dx > world / 2) dx -= world
                if (dx < -world / 2) dx += world
                return IntOffset(
                    (this.size.width / 2 + dx * tilePx).roundToInt(),
                    (this.size.height / 2 + (tile.y - centerY) * tilePx).roundToInt(),
                )
            }
            tiles.forEach { tile ->
                images[Radar.baseTileUrl(dark, zoom, tile.x, tile.y)]?.let {
                    drawImage(it, dstOffset = position(tile), dstSize = size)
                }
            }
            val previous = frames.getOrNull(previousIndex)
            fun radarLayer(radarFrame: RadarFrame, alpha: Float) {
                if (alpha <= 0.01f) return
                tiles.forEach { tile ->
                    images[Radar.radarTileUrl(host!!, radarFrame, zoom, tile.x, tile.y)]?.let {
                        drawImage(it, dstOffset = position(tile), dstSize = size, alpha = alpha)
                    }
                }
            }
            if (frame != null && host != null) {
                if (previous != null && previous != frame) radarLayer(previous, 0.8f * (1f - fade.value))
                radarLayer(frame, 0.8f * fade.value)
            }
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
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Radar de lluvia", style = MaterialTheme.typography.titleMedium)
                        Text(
                            error ?: frame?.let { frameLabel(it) } ?: "Cargando…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { if (zoom > Radar.MIN_ZOOM) zoom-- }) { Text("−") }
                    FilledTonalIconButton(onClick = { if (zoom < Radar.MAX_ZOOM) zoom++ }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Acercar")
                    }
                }
                if (frames.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = { playing = !playing }) {
                            if (playing) {
                                Text("Pausa")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text("Animar")
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
                    "Mapa © OpenStreetMap, © CARTO · Radar RainViewer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun frameLabel(frame: RadarFrame): String {
    val minutes = ((frame.time * 1000 - System.currentTimeMillis()) / 60_000).toInt()
    return when {
        minutes in -5..5 -> "Ahora"
        minutes < 0 -> "Hace ${-minutes} min"
        else -> "En $minutes min (pronóstico)"
    }
}

