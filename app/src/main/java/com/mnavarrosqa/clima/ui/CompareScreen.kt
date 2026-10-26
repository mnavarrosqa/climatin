package com.mnavarrosqa.clima.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.data.windText
import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

/** Cuántas ciudades entran lado a lado: con más, en un teléfono las columnas no se leen. */
private const val MAX_COMPARED = 3

/** Ícono de "comparar": tres barras redondeadas (no está entre los íconos básicos de Material). */
internal val CompareIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Rounded.Compare",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M6.5,20C5.67,20 5,19.33 5,18.5v-7C5,10.67 5.67,10 6.5,10S8,10.67 8,11.5v7C8,19.33 7.33,20 6.5,20z" +
                "M12,20c-0.83,0 -1.5,-0.67 -1.5,-1.5v-13C10.5,4.67 11.17,4 12,4s1.5,0.67 1.5,1.5v13C13.5,19.33 12.83,20 12,20z" +
                "M17.5,20c-0.83,0 -1.5,-0.67 -1.5,-1.5v-4c0,-0.83 0.67,-1.5 1.5,-1.5S19,13.67 19,14.5v4C19,19.33 18.33,20 17.5,20z",
        ),
        fill = SolidColor(Color.Black),
    ).build()
}

/**
 * Dos o tres lugares guardados (incluida "Mi ubicación") lado a lado: ahora, máxima y mínima de
 * hoy, lluvia, viento, UV y la curva de las próximas 12 horas. Las curvas comparten la escala,
 * así se ve de un vistazo cuál está más fresco.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(viewModel: WeatherViewModel, initialIds: List<String>, onBack: () -> Unit) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    // "Mi ubicación" solo si hay algo que mostrar de ella.
    val hasHere = remember { viewModel.cached(CURRENT_PLACE_ID) != null }
    val options = buildList {
        if (hasHere) add(CURRENT_PLACE_ID)
        places.forEach { add(it.id) }
    }
    // Se guarda como texto para que la elección sobreviva a girar la pantalla.
    var chosenText by rememberSaveable { mutableStateOf(initialIds.take(MAX_COMPARED).joinToString("|")) }
    // Una ciudad quitada mientras tanto deja de compararse.
    val chosen = chosenText.split('|').filter { it.isNotEmpty() && it in options }
    fun nameOf(id: String): String =
        if (id == CURRENT_PLACE_ID) tr("Mi ubicación", "My location") else places.firstOrNull { it.id == id }?.shownName ?: id

    val flow = remember(chosen) {
        if (chosen.isEmpty()) flowOf(emptyList<WeatherSnapshot?>()) else combine(chosen.map { viewModel.snapshots(it) }) { it.toList() }
    }
    val initial = remember(chosen) { chosen.map { viewModel.cached(it) } }
    val snapshots by flow.collectAsStateWithLifecycle(initial)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Comparar ciudades", "Compare cities")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Elegí hasta $MAX_COMPARED lugares", "Pick up to $MAX_COMPARED places"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { id ->
                    val selected = id in chosen
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val next = if (selected) chosen - id else chosen + id
                            // Siempre queda al menos uno.
                            if (next.isNotEmpty()) chosenText = next.joinToString("|")
                        },
                        enabled = selected || chosen.size < MAX_COMPARED,
                        label = { Text(nameOf(id), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = when {
                            selected -> ({ Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) })
                            id == CURRENT_PLACE_ID -> ({ Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) })
                            else -> null
                        },
                    )
                }
            }

            if (chosen.size < 2) {
                Text(
                    tr("Sumá otro lugar para compararlos.", "Add another place to compare them."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Ahora: nombre, ícono y temperatura de cada uno.
            CompareSection(null) {
                chosen.forEachIndexed { index, id ->
                    val snapshot = snapshots.getOrNull(index)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            nameOf(id),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (snapshot == null) {
                            Text(tr("Sin pronóstico todavía", "No forecast yet"), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        } else {
                            val current = snapshot.withCurrentFromHours().current
                            Image(
                                painter = painterResource(WeatherCodes.icon(current.code, current.isDay)),
                                contentDescription = WeatherCodes.description(current.code),
                                modifier = Modifier.size(48.dp),
                            )
                            Text(current.temperature.degrees(), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                WeatherCodes.description(current.code),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }

            CompareRow(tr("Hoy", "Today"), snapshots, chosen.size) { snapshot ->
                val today = snapshot.today()
                if (today == null) null else "${today.max.degrees()} / ${today.min.degrees()}"
            }
            CompareRow(tr("Lluvia hoy", "Rain today"), snapshots, chosen.size) { snapshot ->
                val today = snapshot.today()
                val chance = today?.precipitationProbability
                val sum = today?.precipitationSum
                when {
                    chance == null -> null
                    sum != null && sum >= 0.1 -> "$chance% · ${(sum * 10).roundToInt() / 10.0} mm"
                    else -> "$chance%"
                }
            }
            CompareRow(tr("Viento", "Wind"), snapshots, chosen.size) { snapshot ->
                windText(snapshot.withCurrentFromHours().current.windSpeed)
            }
            CompareRow(tr("UV máximo", "Max UV"), snapshots, chosen.size) { snapshot ->
                val uv = snapshot.today()?.uvIndex ?: snapshot.current.uvIndex
                uv?.roundToInt()?.toString()
            }

            // Las próximas 12 horas, con una escala común a todas las curvas.
            val hours = remember(snapshots) { snapshots.map { it?.upcomingHours(12).orEmpty() } }
            val all = hours.flatten().map { it.temperature }
            if (all.isNotEmpty()) {
                val low = all.min()
                val high = all.max()
                CompareSection(tr("Próximas 12 horas", "Next 12 hours")) {
                    hours.forEach { list ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            TemperatureLine(list.map { it.temperature }, low, high)
                            if (list.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${list.minOf { it.temperature }.degrees()} – ${list.maxOf { it.temperature }.degrees()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Un dato por columna, alineado con las columnas de arriba. */
@Composable
private fun CompareRow(
    label: String,
    snapshots: List<WeatherSnapshot?>,
    columns: Int,
    value: (WeatherSnapshot) -> String?,
) {
    CompareSection(label) {
        repeat(columns) { index ->
            val snapshot = snapshots.getOrNull(index)
            Text(
                snapshot?.let(value) ?: "–",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompareSection(label: String?, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Curva chica de temperaturas entre [low] y [high] (la escala de todas las columnas). */
@Composable
private fun TemperatureLine(values: List<Double>, low: Double, high: Double) {
    val dark = LocalDarkTheme.current
    val color = if (values.isEmpty()) MaterialTheme.colorScheme.outline else TempColors.forTheme(TempColors.of(values.average()), dark)
    val description = if (values.isEmpty()) {
        tr("Sin datos", "No data")
    } else {
        tr("De ${values.first().degrees()} a ${values.last().degrees()}", "From ${values.first().degrees()} to ${values.last().degrees()}")
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics { contentDescription = description },
    ) {
        if (values.size < 2) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val pad = 6.dp.toPx()
            val span = (high - low).coerceAtLeast(1.0)
            fun point(index: Int): Offset = Offset(
                pad + (size.width - pad * 2) * index / (values.size - 1),
                pad + (size.height - pad * 2) * (1f - ((values[index] - low) / span).toFloat()),
            )
            val path = Path().apply {
                val first = point(0)
                moveTo(first.x, first.y)
                for (i in 1 until values.size) {
                    val p = point(i)
                    lineTo(p.x, p.y)
                }
            }
            drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // El punto de la hora actual, para que se lea de dónde arranca.
            drawCircle(color, radius = 4.dp.toPx(), center = point(0))
        }
    }
}
