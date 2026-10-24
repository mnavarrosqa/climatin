package com.mnavarrosqa.clima.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.data.WeatherKind

/** Colores del cielo (arriba, medio, horizonte) según el clima y si es de día. */
fun skyColors(kind: WeatherKind, isDay: Boolean): List<Color> = when (kind) {
    WeatherKind.CLEAR -> if (isDay) sky(0xFF1F5FCF, 0xFF7DB9FF, 0xFFBFE0FF) else sky(0xFF060B1F, 0xFF16224A, 0xFF2C3F70)
    WeatherKind.PARTLY -> if (isDay) sky(0xFF2A64C8, 0xFF86BDF5, 0xFFC8E2FA) else sky(0xFF0A1128, 0xFF1D2A50, 0xFF33456F)
    WeatherKind.CLOUDY -> if (isDay) sky(0xFF4C6684, 0xFF93A9C0, 0xFFC2D0DE) else sky(0xFF151C28, 0xFF283344, 0xFF3C495C)
    WeatherKind.FOG -> if (isDay) sky(0xFF7A8796, 0xFFB3BDC8, 0xFFD6DCE2) else sky(0xFF22272F, 0xFF383F49, 0xFF4D5560)
    WeatherKind.DRIZZLE, WeatherKind.RAIN ->
        if (isDay) sky(0xFF33465C, 0xFF62778D, 0xFF8396AA) else sky(0xFF0E141D, 0xFF1E2836, 0xFF2E3B4C)
    WeatherKind.SNOW -> if (isDay) sky(0xFF6A80A0, 0xFFAFC2D8, 0xFFE2EAF3) else sky(0xFF18202D, 0xFF2E3A4D, 0xFF475468)
    WeatherKind.STORM -> if (isDay) sky(0xFF1F2533, 0xFF3A4356, 0xFF535D72) else sky(0xFF090C13, 0xFF171C27, 0xFF262D3B)
}

private fun sky(top: Long, middle: Long, bottom: Long) = listOf(Color(top), Color(middle), Color(bottom))

fun List<Color>.toGradient() = SkyGradient(this[0].toArgb(), this[1].toArgb(), this[2].toArgb())

/** Duración de una vuelta del reloj del cielo. */
const val SKY_CYCLE_MS = 60_000

/**
 * Fondo animado de la app. Al cambiar el clima (por ejemplo, al pasar de ciudad) los dos cielos
 * se funden en vez de saltar.
 */
@Composable
fun AnimatedSky(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier) {
    val clock by rememberInfiniteTransition(label = "cielo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKY_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "reloj",
    )
    Crossfade(targetState = kind to isDay, animationSpec = tween(1200), modifier = modifier, label = "clima") { (k, day) ->
        val colors = skyColors(k, day)
        val top by animateColorAsState(colors[0], tween(1200), label = "arriba")
        val middle by animateColorAsState(colors[1], tween(1200), label = "medio")
        val bottom by animateColorAsState(colors[2], tween(1200), label = "abajo")
        val painter = remember { SkyPainter() }
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                painter.draw(
                    it.nativeCanvas,
                    size.width,
                    size.height,
                    density,
                    clock,
                    k,
                    day,
                    SkyGradient(top.toArgb(), middle.toArgb(), bottom.toArgb()),
                )
            }
        }
    }
}
