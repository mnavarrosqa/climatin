package com.mnavarrosqa.clima.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.data.WeatherKind

/** Colores de cielo arriba y abajo según el clima y si es de día. */
fun skyColors(kind: WeatherKind, isDay: Boolean): Pair<Color, Color> = when (kind) {
    WeatherKind.CLEAR, WeatherKind.PARTLY ->
        if (isDay) Color(0xFF2F6FD0) to Color(0xFF8CC4FF) else Color(0xFF0A1430) to Color(0xFF2A3B66)
    WeatherKind.CLOUDY ->
        if (isDay) Color(0xFF55708E) to Color(0xFFA3B6CA) else Color(0xFF1A2230) to Color(0xFF384457)
    WeatherKind.FOG ->
        if (isDay) Color(0xFF7D8B9B) to Color(0xFFC3CBD4) else Color(0xFF262C35) to Color(0xFF4A525E)
    WeatherKind.DRIZZLE, WeatherKind.RAIN ->
        if (isDay) Color(0xFF3B4F66) to Color(0xFF71879D) else Color(0xFF111924) to Color(0xFF2B3747)
    WeatherKind.SNOW ->
        if (isDay) Color(0xFF6F86A2) to Color(0xFFD0DCEA) else Color(0xFF1C2433) to Color(0xFF465469)
    WeatherKind.STORM ->
        if (isDay) Color(0xFF283041) to Color(0xFF4F586B) else Color(0xFF0D1119) to Color(0xFF262D3B)
}

/** Fondo animado de la app; el dibujo lo hace [SkyPainter], igual que en el fondo de pantalla. */
@Composable
fun AnimatedSky(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier) {
    val (top, bottom) = skyColors(kind, isDay)
    val topColor by animateColorAsState(top, tween(1200), label = "cielo arriba")
    val bottomColor by animateColorAsState(bottom, tween(1200), label = "cielo abajo")
    val clock by rememberInfiniteTransition(label = "cielo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
        label = "reloj",
    )
    val painter = remember { SkyPainter() }
    Canvas(modifier) {
        drawIntoCanvas {
            painter.draw(
                it.nativeCanvas,
                size.width,
                size.height,
                density,
                clock,
                kind,
                isDay,
                topColor.toArgb(),
                bottomColor.toArgb(),
            )
        }
    }
}
