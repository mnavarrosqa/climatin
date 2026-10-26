package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.WeatherSnapshot
import androidx.compose.runtime.produceState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import android.os.PowerManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.ui.theme.Appearance
import com.mnavarrosqa.clima.ui.theme.SkyMotion

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

/** Momento del reloj para el cielo quieto: sin relámpagos ni estrella fugaz (un relámpago a 0.3 lo dejaba blanco). */
const val STILL_CLOCK = 0.2f

/** Duración de una vuelta del reloj del cielo. */
const val SKY_CYCLE_MS = 60_000

/**
 * Fondo animado de la app. Al cambiar el clima (por ejemplo, al pasar de ciudad) los dos cielos
 * se funden en vez de saltar.
 */
@Composable
fun AnimatedSky(
    kind: WeatherKind,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    southern: Boolean = false,
    sunMoon: Boolean = true,
    /** Con el pronóstico, el cielo toma los colores del amanecer y del atardecer del lugar. */
    snapshot: WeatherSnapshot? = null,
) {
    // Los colores de la hora cambian despacio: se revisan una vez por minuto.
    val minute by produceState(0L, snapshot) {
        while (true) {
            value = System.currentTimeMillis() / 60_000
            kotlinx.coroutines.delay(60_000)
        }
    }
    // Ajustes → Pantalla del clima: quieto no anima nada (se dibuja una vez); reducido va a un
    // tercio de la velocidad, para quien se marea o prefiere algo más tranquilo.
    val motion = Appearance.skyMotion
    // Con el ahorro de batería del sistema el cielo queda quieto, como en el fondo de pantalla.
    val context = LocalContext.current
    var powerSave by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // También quieto con "Quitar animaciones" de accesibilidad.
        powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true ||
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val clock: () -> Float = if (motion == SkyMotion.STILL || powerSave) {
        { STILL_CLOCK }
    } else {
        val cycle = if (motion == SkyMotion.REDUCED) SKY_CYCLE_MS * 3 else SKY_CYCLE_MS
        // A unos 30 cuadros por segundo: el cielo se mueve tan despacio que más no se nota, y en
        // pantallas de 120 Hz eran cuatro veces más dibujos de toda la pantalla.
        val value = remember { mutableFloatStateOf(0f) }
        LaunchedEffect(cycle) {
            while (true) {
                // Se espera antes de pedir el cuadro: pedirlo en cada refresco (120 Hz) y descartar
                // la mayoría mantenía despierta la pantalla igual.
                kotlinx.coroutines.delay(32)
                withFrameMillis { now -> value.floatValue = (now % cycle) / cycle.toFloat() }
            }
        }
        val read: () -> Float = { value.floatValue }
        read
    }
    Crossfade(targetState = kind to isDay, animationSpec = tween(1200), modifier = modifier, label = "clima") { (k, day) ->
        val colors = remember(k, day, snapshot, minute) { SkyTime.colors(k, day, snapshot) }
        val top by animateColorAsState(colors[0], tween(1200), label = "arriba")
        val middle by animateColorAsState(colors[1], tween(1200), label = "medio")
        val bottom by animateColorAsState(colors[2], tween(1200), label = "abajo")
        val painter = remember { SkyPainter() }
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas {
                painter.southern = southern
                painter.showSunMoon = sunMoon
                painter.draw(
                    it.nativeCanvas,
                    size.width,
                    size.height,
                    density,
                    clock(),
                    k,
                    day,
                    SkyGradient(top.toArgb(), middle.toArgb(), bottom.toArgb()),
                )
            }
        }
    }
}

/**
 * El mismo cielo, quieto: para las tarjetas de ciudades, donde varios cielos animados a la vez
 * gastan batería sin aportar mucho. Se dibuja una vez y solo se redibuja si cambia el tamaño.
 */
@Composable
fun StillSky(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier, southern: Boolean = false) {
    val painter = remember { SkyPainter() }
    val gradient = remember(kind, isDay) { skyColors(kind, isDay).toGradient() }
    Canvas(modifier) {
        drawIntoCanvas {
            painter.southern = southern
            painter.draw(it.nativeCanvas, size.width, size.height, density, STILL_CLOCK, kind, isDay, gradient)
        }
    }
}
