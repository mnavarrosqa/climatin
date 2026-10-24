package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
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
 * Una ciudad como tarjeta, al estilo del Clima de Apple: el cielo animado de fondo; arriba a la
 * izquierda el nombre grande y debajo la hora del lugar (o la ciudad, en "Mi ubicación"); abajo el
 * estado; a la derecha la temperatura grande y la máxima y mínima.
 * [action] va a la derecha en lugar de la temperatura (por ejemplo, un botón para dar permiso).
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
) {
    val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 1f), 8f))
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(shape)
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier),
    ) {
        StillSky(
            kind = snapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY,
            isDay = snapshot?.current?.isDay ?: true,
            modifier = Modifier.matchParentSize(),
        )
        // Un velo suave abajo para que el estado y la mínima se lean sobre cualquier cielo.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f))),
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            name,
                            style = MaterialTheme.typography.titleLarge.merge(shadow),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall.merge(shadow),
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                snapshot?.let {
                    Text(
                        WeatherCodes.description(it.current.code),
                        style = MaterialTheme.typography.bodyMedium.merge(shadow),
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (action != null) {
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { action() }
            } else if (snapshot != null) {
                Column(
                    Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        snapshot.current.temperature.degrees(),
                        fontSize = 46.sp,
                        lineHeight = 46.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = Flex,
                        color = Color.White,
                        style = shadow,
                    )
                    snapshot.today()?.let { today ->
                        Text(
                            tr("Máx", "H") + " ${today.max.degrees()}  " + tr("Mín", "L") + " ${today.min.degrees()}",
                            style = MaterialTheme.typography.bodySmall.merge(shadow),
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                        )
                    }
                }
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
