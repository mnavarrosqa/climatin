package com.mnavarrosqa.clima.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme

/** Las esquinas de la app: una sola escala, para que tarjetas y mosaicos vecinos coincidan. */
object ClimaShapes {
    /** Tarjetas grandes del pronóstico (horas, días, alertas, avisos). */
    val card = RoundedCornerShape(28.dp)

    /** Mosaicos de detalles y grupos de filas. */
    val tile = RoundedCornerShape(24.dp)

    /** Lo que va adentro de una tarjeta: filas resaltadas, miniaturas, campos. */
    val inner = RoundedCornerShape(16.dp)

    /** Píldoras: pie del pronóstico, etiquetas. */
    val pill = CircleShape
}

/**
 * El material de todo lo que flota sobre el cielo (tarjetas, mosaicos, avisos, pie y la barra de
 * navegación): vidrio oscuro con borde blanco fino y contenido blanco, igual en tema claro y
 * oscuro, como el Clima de Apple. Antes convivían tarjetas casi blancas, una barra azul oscura y
 * avisos opacos de color.
 */
object SkySurface {
    /** Negro apenas azulado: el mismo tono para tarjetas y barra. */
    val Tint = Color(0xFF0B1220)

    /** Opacidad de las tarjetas sobre un cielo oscuro (noche, tormenta). */
    const val CARD_ALPHA_MIN = 0.26f

    /** Sobre un cielo claro (despejado, niebla, nieve de día) hace falta más para leer blanco. */
    const val CARD_ALPHA_MAX = 0.40f

    /** Lo que suma una tarjeta que tiene que destacarse (alertas). */
    const val STRONG_EXTRA = 0.14f

    /** La barra es más opaca: por detrás le pasa el texto blanco de las tarjetas. */
    const val BAR_ALPHA = 0.62f

    val bar = Tint.copy(alpha = BAR_ALPHA)
    val border = Color.White.copy(alpha = 0.14f)
    val barBorder = Color.White.copy(alpha = 0.16f)
    val content = Color.White

    /** Texto secundario: con menos de 0.85 las etiquetas chicas no llegaban a 4:1 de día. */
    val contentVariant = Color.White.copy(alpha = 0.85f)

    /** Lluvia: un celeste fijo y claro. El terciario del tema se volvía violeta con color dinámico. */
    val rain = Color(0xFFA6D2FF)

    /** Acentos de las alertas sobre el vidrio: ámbar para avisos, salmón para las graves. */
    val warning = Color(0xFFFFD98A)
    val severe = Color(0xFFFFB4A6)

    /** Sol fuerte, amanecer y atardecer. */
    val sun = Color(0xFFFFCB6B)

    /** Color de la tarjeta según cuán claro es el cielo ([brightness] de 0 a 1). */
    fun card(brightness: Float, strong: Boolean = false): Color {
        val alpha = CARD_ALPHA_MIN + (CARD_ALPHA_MAX - CARD_ALPHA_MIN) * brightness.coerceIn(0f, 1f)
        return Tint.copy(alpha = (alpha + if (strong) STRONG_EXTRA else 0f).coerceAtMost(0.7f))
    }

    /**
     * Cuán claro es el cielo detrás de las tarjetas, de 0 (noche) a 1 (día despejado, niebla o
     * nieve): la luminancia del medio y del horizonte, que es donde quedan las tarjetas.
     */
    fun brightness(sky: List<Color>): Float {
        if (sky.size < 3) return 1f
        val luminance = (sky[1].luminance() + sky[2].luminance()) / 2f
        return ((luminance - 0.08f) / 0.37f).coerceIn(0f, 1f)
    }

    /**
     * Velo detrás de la lista. Arriba, donde la temperatura va directo sobre el cielo, siempre es
     * fuerte; abajo depende del cielo: casi nada de noche, bastante de día. Con el vidrio encima,
     * el blanco queda arriba de 4.5:1 incluso sobre el horizonte de un día de nieve.
     */
    fun veil(brightness: Float): Brush {
        val base = 0.08f + 0.18f * brightness.coerceIn(0f, 1f)
        return Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.30f + 0.04f * brightness.coerceIn(0f, 1f)),
            0.5f to Color.Black.copy(alpha = base),
            1f to Color.Black.copy(alpha = base),
        )
    }
}

/** Cuán claro es el cielo de fondo (ver [SkySurface.brightness]). Sin dato, se supone claro: el vidrio más oscuro. */
val LocalSkyBrightness = compositionLocalOf { 1f }

/**
 * Los colores de lo que se dibuja sobre una superficie. Los gráficos (curva por hora, mosaicos) los
 * piden acá y no al tema: así el mismo dibujo sirve sobre el vidrio del cielo (blanco) y sobre una
 * hoja o un diálogo (colores del tema).
 */
@Immutable
data class SurfaceInk(
    val content: Color,
    val variant: Color,
    /** Fondo de barras y anillos vacíos. */
    val track: Color,
    val divider: Color,
    /** Fondo suave de lo elegido o destacado (hoy, la hora tocada). */
    val highlight: Color,
    val accent: Color,
    val rain: Color,
    val sun: Color,
    /** Aro alrededor de un punto, para despegarlo de la línea que lo cruza. */
    val knockout: Color,
    /** Si el fondo es oscuro: las escalas de color no se oscurecen. */
    val onDark: Boolean,
)

private val SkyInk = SurfaceInk(
    content = SkySurface.content,
    variant = SkySurface.contentVariant,
    track = Color.White.copy(alpha = 0.16f),
    divider = Color.White.copy(alpha = 0.14f),
    highlight = Color.White.copy(alpha = 0.12f),
    accent = SkySurface.rain,
    rain = SkySurface.rain,
    sun = SkySurface.sun,
    knockout = Color(0xFF1C2533),
    onDark = true,
)

/** Null fuera del vidrio: ahí valen los colores del tema. */
val LocalSurfaceInk = compositionLocalOf<SurfaceInk?> { null }

/** Los colores para dibujar acá: los del vidrio si estamos sobre el cielo, si no los del tema. */
@Composable
fun surfaceInk(): SurfaceInk {
    val onGlass = LocalSurfaceInk.current
    if (onGlass != null) return onGlass
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkTheme.current
    return SurfaceInk(
        content = colors.onSurface,
        variant = colors.onSurfaceVariant,
        track = colors.onSurface.copy(alpha = 0.10f),
        divider = colors.onSurface.copy(alpha = 0.12f),
        highlight = colors.primary.copy(alpha = 0.12f),
        accent = colors.primary,
        // En tema claro el celeste del vidrio no se lee sobre blanco.
        rain = if (dark) SkySurface.rain else Color(0xFF1E6FD0),
        sun = if (dark) Color(0xFFFFB74D) else Color(0xFFC25100),
        knockout = colors.surface,
        onDark = dark,
    )
}

/**
 * Una superficie de vidrio sobre el cielo. Lo de adentro recibe blanco como color de contenido y
 * [LocalSurfaceInk] con los colores del vidrio. [strong] la oscurece un poco más (alertas).
 */
@Composable
fun SkyGlass(
    modifier: Modifier = Modifier,
    shape: Shape = ClimaShapes.card,
    strong: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        color = SkySurface.card(LocalSkyBrightness.current, strong),
        contentColor = SkySurface.content,
        shape = shape,
        border = BorderStroke(1.dp, SkySurface.border),
        modifier = modifier,
    ) {
        CompositionLocalProvider(LocalSurfaceInk provides SkyInk, content = content)
    }
}

/** Lo mismo que [SkyGlass] para lo que arma su contenido fuera de un Surface (los mosaicos). */
@Composable
fun OnSkyGlass(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSurfaceInk provides SkyInk,
        androidx.compose.material3.LocalContentColor provides SkySurface.content,
        content = content,
    )
}

/** Título de tarjeta o mosaico en versalitas chicas y espaciadas, como en el Clima de Apple. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = surfaceInk().variant,
        modifier = modifier,
    )
}

/**
 * Título de una sección fuera de las tarjetas (grupos de Ajustes, partes de una hoja). Se llama
 * distinto del `SectionTitle` privado de Ajustes y de los widgets para no chocar con ellos.
 */
@Composable
fun ClimaSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Nombre, valor y nota de un dato: el interior de un mosaico, sobre el cielo o sobre el tema. */
@Composable
fun TileFacts(label: String, value: String, note: String? = null, noteIcon: ImageVector? = null, modifier: Modifier = Modifier) {
    val ink = surfaceInk()
    Column(modifier) {
        CardLabel(label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = ink.content)
        if (note != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (noteIcon != null) {
                    Icon(noteIcon, contentDescription = null, tint = ink.variant, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(note, style = MaterialTheme.typography.bodySmall, color = ink.variant)
            }
        }
    }
}

/** Un mosaico de dato sobre una superficie del tema (hojas, comparar): el mismo aspecto que los de detalles. */
@Composable
fun FactTile(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = ClimaShapes.tile,
        modifier = modifier,
    ) {
        TileFacts(label, value, note, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
    }
}

// Íconos que no están entre los básicos de Material (solo hay material-icons-core): los mismos
// trazos de Material, a mano. Reemplazan a los emoji y a las flechas de texto.
private fun climaVector(name: String, path: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

/** Viento. */
internal val WindIcon: ImageVector by lazy {
    climaVector(
        "Rounded.Air",
        "M14.5 17c0 1.65-1.35 3-3 3s-3-1.35-3-3h2c0 .55.45 1 1 1s1-.45 1-1-.45-1-1-1H2v-2h9.5c1.65 0 3 1.35 3 3z" +
            "M19 6.5C19 4.57 17.43 3 15.5 3S12 4.57 12 6.5h2c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5S16.33 8 15.5 8H2v2h13.5c1.93 0 3.5-1.57 3.5-3.5z" +
            "M18.5 11H2v2h16.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5v2c1.93 0 3.5-1.57 3.5-3.5S20.43 11 18.5 11z",
    )
}

/** Sol fuerte. */
internal val SunIcon: ImageVector by lazy {
    climaVector(
        "Rounded.WbSunny",
        "M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.79 1.42-1.41z" +
            "M4 10.5H1v2h3v-2z" +
            "M13 .55h-2V3.5h2V.55z" +
            "M20.45 4.46l-1.41-1.41-1.79 1.79 1.41 1.41 1.79-1.79z" +
            "M17.24 18.16l1.79 1.8 1.41-1.41-1.8-1.79-1.4 1.4z" +
            "M20 10.5v2h3v-2h-3z" +
            "M12 5.5c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6z" +
            "M11 22.45h2V19.5h-2v2.95z" +
            "M3.55 18.54l1.41 1.41 1.79-1.8-1.41-1.41-1.79 1.8z",
    )
}

/** Sale (el sol, la luna) o sube (la presión). */
internal val ArrowUpIcon: ImageVector by lazy {
    climaVector("Rounded.ArrowUpward", "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z")
}

/** Se pone o baja. */
internal val ArrowDownIcon: ImageVector by lazy {
    climaVector("Rounded.ArrowDownward", "M20 12l-1.41-1.41L13 16.17V4h-2v12.17l-5.58-5.59L4 12l8 8 8-8z")
}
