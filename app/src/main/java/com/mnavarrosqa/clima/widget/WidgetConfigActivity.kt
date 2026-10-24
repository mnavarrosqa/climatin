package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Place
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.ui.theme.ClimaTheme
import kotlinx.coroutines.launch

/** Se abre al agregar un widget (o al tocar el lápiz) para elegir ciudad y estilo. */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = WeatherRepository.get(this)
        val places = repository.places()
        // "De un vistazo" y "Burbuja" arrancan con otro estilo por defecto.
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
        val isIndicator = provider.endsWith("IndicadorWidgetReceiver")
        val defaultStyle = when {
            provider.endsWith("VistazoWidgetReceiver") -> WidgetStyle.TRANSPARENT
            provider.endsWith("BurbujaWidgetReceiver") -> WidgetStyle.COLOR
            else -> WidgetStyle.SYSTEM
        }

        setContent {
            ClimaTheme {
                var placeId by remember { mutableStateOf(CURRENT_PLACE_ID) }
                var style by remember { mutableStateOf(defaultStyle) }
                var metric by remember { mutableStateOf(Metric.UV) }
                LaunchedEffect(Unit) {
                    // Al reconfigurar, arranca con lo que el widget ya tenía.
                    runCatching {
                        val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
                        val prefs = getAppWidgetState(this@WidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId)
                        placeId = repository.resolvePlaceId(prefs[WidgetPrefs.PLACE])
                        style = WidgetStyle.from(prefs[WidgetPrefs.STYLE], defaultStyle)
                        metric = Metric.from(prefs[WidgetPrefs.METRIC])
                    }
                }
                ConfigScreen(
                    places = places,
                    placeId = placeId,
                    onPlace = { placeId = it },
                    style = style,
                    onStyle = { style = it },
                    metric = metric.takeIf { isIndicator },
                    onMetric = { metric = it },
                    previewTemperature = repository.cached(placeId)?.current?.temperature?.degrees() ?: "22°",
                    previewCode = repository.cached(placeId)?.current?.code ?: 2,
                    onDone = {
                        lifecycleScope.launch {
                            val context = this@WidgetConfigActivity
                            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(context, glanceId) { prefs ->
                                prefs[WidgetPrefs.PLACE] = placeId
                                prefs[WidgetPrefs.STYLE] = style.name
                                if (isIndicator) prefs[WidgetPrefs.METRIC] = metric.name
                            }
                            updateWeatherWidgets(context)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    places: List<Place>,
    placeId: String,
    onPlace: (String) -> Unit,
    style: WidgetStyle,
    onStyle: (WidgetStyle) -> Unit,
    metric: Metric?,
    onMetric: (Metric) -> Unit,
    previewTemperature: String,
    previewCode: Int,
    onDone: () -> Unit,
) {
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(tr("Personalizá el widget", "Customize the widget")) }) },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(tr("Listo", "Done")) }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { StylePreview(style, previewTemperature, previewCode) }
            if (metric != null) {
                item { SectionTitle(tr("Dato", "Data")) }
                item {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Metric.entries.forEach { option ->
                            FilterChip(
                                selected = option == metric,
                                onClick = { onMetric(option) },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
            item { SectionTitle(tr("Estilo", "Style")) }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WidgetStyle.entries.forEach { option ->
                        FilterChip(
                            selected = option == style,
                            onClick = { onStyle(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
            item { SectionTitle(tr("Ciudad", "City")) }
            item {
                PlaceRow(
                    title = tr("Donde estoy", "My location"),
                    subtitle = tr("Sigue tu ubicación", "Follows your location"),
                    current = true,
                    selected = placeId == CURRENT_PLACE_ID,
                    onClick = { onPlace(CURRENT_PLACE_ID) },
                )
            }
            items(places, key = { it.id }) { place ->
                PlaceRow(
                    title = place.name,
                    subtitle = place.detail,
                    current = false,
                    selected = placeId == place.id,
                    onClick = { onPlace(place.id) },
                )
            }
            if (places.isEmpty()) {
                item {
                    Text(
                        tr("Podés agregar más ciudades desde la app, en Ciudades y ajustes.", "You can add more cities in the app, under Cities and settings."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

/** Muestra cómo queda el estilo elegido, sobre un fondo de pantalla de ejemplo. */
@Composable
private fun StylePreview(style: WidgetStyle, temperature: String, code: Int) {
    val colors = MaterialTheme.colorScheme
    val (background, text) = when (style) {
        WidgetStyle.SYSTEM -> colors.surfaceContainerHigh to colors.onSurface
        WidgetStyle.COLOR -> colors.primaryContainer to colors.onPrimaryContainer
        WidgetStyle.TRANSPARENT, WidgetStyle.SKY -> Color.Transparent to Color.White
    }
    val skyBrush = Brush.verticalGradient(skyColors(WeatherCodes.kind(code), isDay = true))
    Box(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(colors.tertiary, colors.primary))),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = background,
            contentColor = text,
            shape = RoundedCornerShape(24.dp),
            modifier = if (style == WidgetStyle.SKY) Modifier.background(skyBrush, RoundedCornerShape(24.dp)) else Modifier,
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(temperature, fontSize = 40.sp)
                    Text(WeatherCodes.description(code), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(16.dp))
                Image(
                    painter = painterResource(WeatherCodes.icon(code)),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun PlaceRow(title: String, subtitle: String?, current: Boolean, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { s -> @Composable { Text(s) } },
        leadingContent = {
            Icon(if (current) Icons.Rounded.LocationOn else Icons.Rounded.Place, contentDescription = null)
        },
        trailingContent = { RadioButton(selected = selected, onClick = onClick) },
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    )
}
