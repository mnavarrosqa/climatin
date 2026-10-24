package com.mnavarrosqa.clima.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import kotlin.math.PI
import kotlin.math.sin

/**
 * Ícono del clima con un movimiento suave según el tipo: el sol gira, la luna late, las nubes
 * flotan de costado, la lluvia y la nieve suben y bajan, la tormenta parpadea.
 */
@Composable
fun AnimatedWeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val kind = WeatherCodes.kind(code)
    val clock by rememberInfiniteTransition(label = "icono").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "reloj",
    )
    val wave = sin(clock * 2 * PI.toFloat() * 3)
    Image(
        painter = painterResource(WeatherCodes.icon(code, isDay)),
        contentDescription = WeatherCodes.description(code),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                when (kind) {
                    WeatherKind.CLEAR -> if (isDay) {
                        rotationZ = clock * 360f
                    } else {
                        val pulse = 1f + 0.04f * wave
                        scaleX = pulse
                        scaleY = pulse
                    }
                    WeatherKind.PARTLY, WeatherKind.CLOUDY, WeatherKind.FOG ->
                        translationX = wave * 4.dp.toPx()
                    WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.SNOW ->
                        translationY = wave * 3.dp.toPx()
                    WeatherKind.STORM -> {
                        translationY = wave * 2.dp.toPx()
                        // Un parpadeo corto cada vuelta, como un relámpago.
                        alpha = if ((clock * 4) % 1f in 0.0f..0.04f) 0.55f else 1f
                    }
                }
            },
    )
}
