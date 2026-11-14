package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.rainSoonText
import com.mnavarrosqa.clima.data.isWet
import androidx.compose.runtime.remember
import com.mnavarrosqa.clima.ui.theme.Flex

/**
 * Una ciudad como tarjeta, al estilo del Clima de Apple: el cielo animado de fondo; arriba a la
 * izquierda el nombre grande y debajo la hora del lugar (o la ciudad, en "Mi ubicación"); abajo el
 * estado; a la derecha la temperatura grande y la máxima y mínima.
 * [action] va a la derecha en lugar de la temperatura (por ejemplo, un botón para dar permiso).
 * [menu] va en el borde derecho (el "⋮" de las ciudades guardadas).
 */
@Composable
fun CityCard(
    name: String,
    subtitle: String?,
    snapshot: WeatherSnapshot?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
    /** Deja a la derecha el lugar del "⋮" aunque no lo tenga, para que las temperaturas queden en columna. */
    alignWithMenu: Boolean = false,
) {
    // La misma sombra y los mismos blancos que la temperatura grande del pronóstico.
    val shadow = TextStyle().onSky()
    val shape = ClimaShapes.tile
    val today = snapshot?.today()
    val note = remember(snapshot) { snapshot?.let(::cityNote) }
    val minMax = today?.let { tr("Máx", "High") + " ${it.max.degrees()}  " + tr("Mín", "Low") + " ${it.min.degrees()}" }
    // TalkBack la lee de una vez: "Córdoba, 14:30, 23°, despejado, máx 25°, mín 12°".
    val spoken = listOfNotNull(
        name,
        subtitle,
        snapshot?.current?.temperature?.degrees(),
        snapshot?.current?.code?.let(WeatherCodes::description),
        today?.let { tr("máx", "high") + " ${it.max.degrees()}, " + tr("mín", "low") + " ${it.min.degrees()}" },
        note?.text,
    ).joinToString(", ")
    val kind = snapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY
    val isDay = snapshot?.current?.isDay ?: true
    // Cuán claro es el cielo de la tarjeta: en un nublado de día las nubes blancas se comían el texto.
    val brightness = remember(kind, isDay, snapshot) { SkySurface.brightness(SkyTime.colors(kind, isDay, snapshot)) }
    Box(
        modifier
            .fillMaxWidth()
            // Mínimo y no fijo: con letra grande el número de 46 sp no se corta.
            .heightIn(min = 116.dp)
            .clip(shape)
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .semantics { contentDescription = spoken },
    ) {
        StillSky(
            kind = kind,
            isDay = isDay,
            modifier = Modifier.matchParentSize(),
            southern = (snapshot?.latitude ?: 0.0) < 0,
            snapshot = snapshot,
            sunMoon = false,
            starFraction = 0.35f,
        )
        // Un velo arriba y abajo para que el nombre y el estado se lean sobre cualquier cielo: casi
        // nada de noche y más cuanto más claro el cielo. Más liviano que el del pronóstico
        // (SkySurface.veil): acá no hay tarjetas de vidrio encima.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.16f + 0.16f * brightness),
                        0.45f to Color.Black.copy(alpha = 0.04f + 0.10f * brightness),
                        1f to Color.Black.copy(alpha = 0.26f + 0.12f * brightness),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                // La altura del contenido (al menos la de siempre), así las columnas reparten el espacio.
                .heightIn(min = 116.dp)
                .height(IntrinsicSize.Min)
                .padding(start = 18.dp, end = if (menu != null || alignWithMenu) 4.dp else 18.dp, top = 14.dp, bottom = 14.dp),
        ) {
            // Los textos ya van en la descripción de la tarjeta: no se leen de nuevo uno por uno.
            Column(
                Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { },
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = SkySurface.content, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            name,
                            style = MaterialTheme.typography.titleLarge.merge(shadow),
                            fontWeight = FontWeight.SemiBold,
                            color = SkySurface.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // La nota va en la misma línea que la hora: en una línea aparte, las tarjetas con
                    // nota quedaban más altas que las otras y la lista se veía despareja.
                    if (subtitle != null || note != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall.merge(shadow),
                                    fontWeight = FontWeight.Medium,
                                    color = SkySurface.contentVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // Si no entra todo, se recorta la hora o el apodo, no la nota.
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (note != null) {
                                if (subtitle != null) Spacer(Modifier.width(8.dp))
                                NotePill(note)
                            }
                        }
                    }
                }
                snapshot?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painterResource(WeatherCodes.icon(it.current.code, it.current.isDay)),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            WeatherCodes.description(it.current.code),
                            style = MaterialTheme.typography.bodyMedium.merge(shadow),
                            fontWeight = FontWeight.Medium,
                            color = SkySurface.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (action != null) {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { action() }
            } else if (snapshot != null) {
                Column(
                    Modifier.fillMaxHeight().clearAndSetSemantics { },
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        snapshot.current.temperature.degrees(),
                        fontSize = 46.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = Flex,
                        color = SkySurface.content,
                        style = shadow,
                    )
                    if (minMax != null) {
                        Text(
                            minMax,
                            style = MaterialTheme.typography.bodySmall.merge(shadow),
                            fontWeight = FontWeight.Medium,
                            color = SkySurface.content,
                        )
                    }
                }
            }
            if (menu != null) {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.TopCenter) { menu() }
            } else if (alignWithMenu) {
                // El ancho de un IconButton, como el "⋮" de las otras tarjetas.
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

/** Lo que conviene saber de una ciudad sin abrirla: una alerta, lluvia que llega o lluvia probable hoy. */
class CityNote(val text: String, val level: Level) {
    enum class Level { ALERT, SEVERE, RAIN }
}

/** La nota más importante de la ciudad, o null si no hay nada que avisar. */
fun cityNote(snapshot: WeatherSnapshot): CityNote? {
    snapshot.alerts.firstOrNull { !it.isExpired() }?.let { alert ->
        return CityNote(tr("Alerta: ", "Alert: ") + alert.event, if (alert.isSevere) CityNote.Level.SEVERE else CityNote.Level.ALERT)
    }
    snapshot.minutesUntilRain()?.let { return CityNote(rainSoonText(it), CityNote.Level.RAIN) }
    // Si ya llueve, el estado de abajo lo dice; si no, la probabilidad de hoy cuando vale la pena.
    if (!WeatherCodes.kind(snapshot.current.code).isWet) {
        snapshot.today()?.precipitationProbability?.takeIf { it >= 40 }?.let {
            return CityNote(tr("Lluvia hoy $it%", "Rain today $it%"), CityNote.Level.RAIN)
        }
    }
    return null
}

/** La nota como una píldora chica: naranja o roja para las alertas, oscura con celeste para la lluvia. */
@Composable
private fun NotePill(note: CityNote) {
    val background = when (note.level) {
        CityNote.Level.SEVERE -> Color(0xE6C62828)
        CityNote.Level.ALERT -> Color(0xE6E65100)
        CityNote.Level.RAIN -> Color.Black.copy(alpha = 0.30f)
    }
    val content = if (note.level == CityNote.Level.RAIN) SkySurface.rain else Color.White
    Text(
        note.text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(ClimaShapes.pill)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/**
 * Reordenar arrastrando dentro de una LazyColumn, sin librerías: el ítem arrastrado sigue al dedo
 * y, cuando su centro pasa sobre otra ciudad, intercambian lugar. Las claves de la lista son los ids.
 */
class CityReorder(private val listState: LazyListState) {
    var order by mutableStateOf<List<String>>(emptyList())
    var dragging by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set

    private var original: List<String> = emptyList()

    fun start(id: String) {
        original = order
        dragging = id
        offset = 0f
    }

    /** Devuelve true si el ítem cambió de lugar (para vibrar). */
    fun drag(delta: Float): Boolean {
        val id = dragging ?: return false
        offset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == id } ?: return false
        val center = current.offset + current.size / 2 + offset
        val target = items.firstOrNull { info ->
            val key = info.key as? String
            key != null && key != id && key in order && center.toInt() in info.offset..(info.offset + info.size)
        } ?: return false
        val from = order.indexOf(id)
        val to = order.indexOf(target.key as String)
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // El ítem ya está en su lugar nuevo: se descuenta lo que se movió para que no salte.
        offset += current.offset - target.offset
        return true
    }

    /**
     * Cuánto correr la lista en este cuadro si el ítem arrastrado está cerca de un borde: más rápido
     * cuanto más se acerca. 0 si no hay que correrla.
     */
    fun edgeScroll(threshold: Float, maxStep: Float): Float {
        val id = dragging ?: return 0f
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return 0f
        val top = item.offset + offset
        val bottom = top + item.size
        // Los offsets cuentan desde donde termina el relleno de arriba (la barra superior).
        val end = (info.viewportEndOffset - info.afterContentPadding).toFloat()
        return when {
            top < threshold && listState.canScrollBackward -> -maxStep * ((threshold - top) / threshold).coerceIn(0f, 1f)
            bottom > end - threshold && listState.canScrollForward -> maxStep * ((bottom - end + threshold) / threshold).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    /**
     * La lista se corrió [by] px: el ítem se queda bajo el dedo y se revisa si ahora pasó sobre
     * otro. Devuelve true si cambió de lugar.
     */
    fun scrolled(by: Float): Boolean {
        if (dragging == null) return false
        offset += by
        return drag(0f)
    }

    /** Termina el arrastre y devuelve (id, cuántos lugares se movió). */
    fun end(): Pair<String, Int>? {
        val id = dragging ?: return null
        dragging = null
        offset = 0f
        val moved = order.indexOf(id) - original.indexOf(id)
        return if (moved != 0) id to moved else null
    }
}
