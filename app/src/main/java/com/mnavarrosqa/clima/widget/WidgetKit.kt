package com.mnavarrosqa.clima.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mnavarrosqa.clima.data.Day
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import java.time.LocalDate

/**
 * Piezas comunes de los widgets, sacadas del widget Clima (1.28) para que todos se vean como un
 * mismo juego: encabezado, filas de días con barra, fila de horas y una sola escala de letras.
 */
object WidgetType {
    /** Nombre de la ciudad (va en Medium y con el color secundario). */
    val place: TextUnit = 13.sp
    /** Temperatura grande: compacta, mediana y grande. Siempre en peso Normal, como en Clima. */
    val bigCompact: TextUnit = 36.sp
    val bigMedium: TextUnit = 40.sp
    val bigLarge: TextUnit = 46.sp
    /** Estado ("Nublado"), en Medium. */
    val condition: TextUnit = 13.sp
    /** Máxima y mínima. */
    val range: TextUnit = 12.sp
    /** Etiqueta de una fila (día, ciudad), en Medium. */
    val rowLabel: TextUnit = 14.sp
    /** Textos chicos: horas, probabilidad de lluvia, notas. */
    val caption: TextUnit = 12.sp
}

/**
 * Tamaño del encabezado. [reserve] es el alto aproximado que ocupa con letra normal (hay que
 * multiplicarlo por [fontScale]); sirve para repartir lo que queda entre horas, días o gráfico.
 */
enum class HeaderSize(val reserve: Dp) {
    /** Una línea: ciudad a la izquierda, ícono y temperatura a la derecha. Para widgets bajos. */
    INLINE(40.dp),
    COMPACT(78.dp),
    FULL(90.dp),
}

/** Máxima y mínima de hoy, en una línea ("Máx 25°  Mín 12°"). */
fun todayRangeText(snapshot: WeatherSnapshot): String? = snapshot.today()?.let {
    tr("Máx", "H") + " ${it.max.degrees()}  " + tr("Mín", "L") + " ${it.min.degrees()}"
}

/**
 * Encabezado como el del Clima de Apple: ciudad y temperatura grande a la izquierda; ícono,
 * estado y máxima y mínima a la derecha.
 */
@Composable
fun NowHeader(snapshot: WeatherSnapshot, colors: WidgetColors, size: HeaderSize = HeaderSize.FULL) {
    val current = snapshot.current
    if (size == HeaderSize.INLINE) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                PlaceLabel(snapshot, colors, fontSize = WidgetType.place, fontWeight = FontWeight.Medium)
            }
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = WeatherCodes.description(current.code),
                modifier = GlanceModifier.size(24.dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                current.temperature.degrees(),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = 28.sp),
            )
        }
        return
    }
    val compact = size == HeaderSize.COMPACT
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            PlaceLabel(snapshot, colors, fontSize = WidgetType.place, fontWeight = FontWeight.Medium)
            Text(
                current.temperature.degrees(),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = if (compact) WidgetType.bigCompact else WidgetType.bigLarge),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Image(
                provider = ImageProvider(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = null,
                modifier = GlanceModifier.size(if (compact) 26.dp else 32.dp),
            )
            Text(
                WeatherCodes.description(current.code),
                maxLines = 1,
                style = TextStyle(color = colors.text, fontSize = WidgetType.condition, fontWeight = FontWeight.Medium),
            )
            todayRangeText(snapshot)?.takeIf { widgetShows(WidgetPart.RANGE) }?.let {
                Text(it, maxLines = 1, style = TextStyle(color = colors.text, fontSize = WidgetType.range))
            }
        }
    }
}

/** Lo fijo de una fila de día (todo menos la barra): día 64, ícono 22, lluvia 38, mínima 40, márgenes 6+6 y máxima 36. */
private val DayRowFixed = 212.dp
private val DayRowRain = 38.dp

/** Barra más angosta que esto ya no se lee: mejor sacar la columna de lluvia. */
private val MinBar = 48.dp

/**
 * Lista de días con barra de mínima a máxima (día | ícono | lluvia | mín | barra | máx), como
 * los widgets grandes de Apple y las tarjetas de la app. [innerWidth] es el ancho sin el padding
 * del widget. La columna de lluvia sale si algún día la tiene y entra sin achicar la barra de más.
 */
@Composable
fun DayRangeList(snapshot: WeatherSnapshot, days: List<Day>, colors: WidgetColors, innerWidth: Dp, rowHeight: Dp) {
    if (days.isEmpty()) return
    val today = snapshot.nowAtPlace().toLocalDate()
    val weekMin = days.minOf { it.min }
    val weekMax = days.maxOf { it.max }
    val anyRain = days.any { (it.precipitationProbability ?: 0) >= 30 }
    val showRain = anyRain && innerWidth - DayRowFixed >= MinBar
    val fixed = if (showRain) DayRowFixed else DayRowFixed - DayRowRain
    val barWidth = (innerWidth - fixed).coerceAtLeast(24.dp)
    days.forEach { day ->
        DayRangeRow(snapshot, day, today, weekMin, weekMax, colors, barWidth, showRain, rowHeight)
    }
}

/** Una fila de [DayRangeList]; tocarla abre ese día en la app. */
@Composable
fun DayRangeRow(
    snapshot: WeatherSnapshot,
    day: Day,
    today: LocalDate,
    weekMin: Double,
    weekMax: Double,
    colors: WidgetColors,
    barWidth: Dp,
    showRain: Boolean,
    height: Dp,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val isToday = day.localDate == today
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(height).clickable(openDay(snapshot.placeId, day.date)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel(day.localDate, today),
            maxLines = 1,
            modifier = GlanceModifier.width(64.dp),
            style = TextStyle(color = colors.text, fontSize = WidgetType.rowLabel, fontWeight = FontWeight.Medium),
        )
        Image(
            provider = ImageProvider(WeatherCodes.icon(day.code)),
            contentDescription = WeatherCodes.description(day.code),
            modifier = GlanceModifier.size(22.dp),
        )
        if (showRain) {
            Text(
                day.precipitationProbability?.takeIf { it >= 30 }?.let { "$it%" } ?: "",
                maxLines = 1,
                modifier = GlanceModifier.width(DayRowRain),
                style = TextStyle(color = colors.textVariant, fontSize = WidgetType.caption, textAlign = TextAlign.Center),
            )
        }
        Text(
            day.min.degrees(),
            maxLines = 1,
            modifier = GlanceModifier.width(40.dp),
            style = TextStyle(color = colors.textVariant, fontSize = WidgetType.rowLabel, textAlign = TextAlign.End),
        )
        Spacer(GlanceModifier.width(6.dp))
        Image(
            provider = ImageProvider(
                WidgetBitmaps.rangeBar(
                    (barWidth.value * density).toInt(),
                    (6 * density).toInt(),
                    weekMin,
                    weekMax,
                    day.min,
                    day.max,
                    snapshot.current.temperature.takeIf { isToday },
                    // Sobre fondos claros, la pista blanca no se ve: va en gris.
                    track = if (colors.transparent) 0x33FFFFFF else 0x1F000000,
                ),
            ),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.width(barWidth).height(6.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            day.max.degrees(),
            maxLines = 1,
            modifier = GlanceModifier.width(36.dp),
            style = TextStyle(color = colors.text, fontSize = WidgetType.rowLabel, fontWeight = FontWeight.Medium),
        )
    }
}

/**
 * Fila de próximas horas. Sobre el cielo (o sin fondo) va suelta, como en el Clima de Apple;
 * sobre un fondo de color, en un recuadro.
 */
@Composable
fun HoursRow(snapshot: WeatherSnapshot, count: Int, colors: WidgetColors) {
    val hours = snapshot.upcomingHours(count)
    val loose = colors.transparent
    val textColor = if (loose) colors.text else colors.onChip
    var modifier = GlanceModifier
        .fillMaxWidth()
        .clickable(openDay(snapshot.placeId, snapshot.nowAtPlace().toLocalDate().toString()))
    if (!loose) modifier = modifier.background(colors.chip).cornerRadius(16.dp).padding(vertical = 6.dp)
    Row(modifier = modifier) {
        hours.forEachIndexed { index, hour ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (index == 0) tr("Ahora", "Now") else shortHour(hour.dateTime),
                    maxLines = 1,
                    style = TextStyle(color = textColor, fontSize = WidgetType.caption, fontWeight = FontWeight.Medium),
                )
                Image(
                    provider = ImageProvider(WeatherCodes.icon(hour.code, hour.isDay)),
                    contentDescription = WeatherCodes.description(hour.code),
                    modifier = GlanceModifier.size(24.dp).padding(vertical = 2.dp),
                )
                Text(
                    hour.temperature.degrees(),
                    maxLines = 1,
                    style = TextStyle(color = textColor, fontSize = WidgetType.rowLabel, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

/**
 * Escala del UV de la OMS: la misma que `UvColors` de ui/DetailTiles.kt. Allá es privada, así
 * que se repite acá; si cambia una, cambiar la otra.
 */
private val UvScale = intArrayOf(
    0xFF4CAF50.toInt(),
    0xFFFFEB3B.toInt(),
    0xFFFF9800.toInt(),
    0xFFF44336.toInt(),
    0xFF9C27B0.toInt(),
)

/** Color del UV, con los mismos cortes que el texto ("Bajo" < 3, "Moderado" < 6…). */
fun uvColor(uv: Double): Int {
    val level = when {
        uv < 3 -> 0
        uv < 6 -> 1
        uv < 8 -> 2
        uv < 11 -> 3
        else -> 4
    }
    return UvScale[level]
}
