package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.ui.theme.Flex

/**
 * Una ciudad como tarjeta: su cielo animado de fondo, el nombre y la temperatura de ahora.
 * [action] va a la derecha en lugar de la temperatura (por ejemplo, un botón para dar permiso).
 */
@Composable
fun CityCard(
    name: String,
    detail: String?,
    snapshot: WeatherSnapshot?,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        AnimatedSky(
            kind = snapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY,
            isDay = snapshot?.current?.isDay ?: true,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val subtitle = snapshot?.let { WeatherCodes.description(it.current.code) } ?: detail
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                snapshot?.today()?.let { today ->
                    Text(
                        tr("Máx", "High") + " ${today.max.degrees()} · " + tr("Mín", "Low") + " ${today.min.degrees()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            if (action != null) {
                action()
            } else if (snapshot != null) {
                Image(
                    painter = painterResource(WeatherCodes.icon(snapshot.current.code, snapshot.current.isDay)),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    snapshot.current.temperature.degrees(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = Flex,
                    color = Color.White,
                )
            }
        }
    }
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

    /** Termina el arrastre y devuelve (id, cuántos lugares se movió). */
    fun end(): Pair<String, Int>? {
        val id = dragging ?: return null
        dragging = null
        offset = 0f
        val moved = order.indexOf(id) - original.indexOf(id)
        return if (moved != 0) id to moved else null
    }
}
