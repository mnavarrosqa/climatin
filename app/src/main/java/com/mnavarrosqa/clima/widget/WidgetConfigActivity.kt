package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.ui.theme.ClimaTheme
import kotlinx.coroutines.launch

/**
 * Se abre al agregar un widget, al tocar el lápiz o desde "Mis widgets" en la app. Muestra el
 * widget real mientras se elige ciudad, estilo, color, opacidad, esquinas, qué mostrar y qué hace
 * al tocarlo.
 */
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
        val kind = WidgetCatalog.kindOf(this, appWidgetId)
        val defaultStyle = kind?.defaultStyle ?: WidgetStyle.SYSTEM
        val others = WidgetCatalog.placed(this).filter { it.second != appWidgetId }

        suspend fun read(id: Int): WidgetConfig? = runCatching {
            val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(id)
            val prefs = getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
            WidgetConfig.from(prefs, defaultStyle, repository::resolvePlaceId)
        }.getOrNull()

        setContent {
            ClimaTheme {
                var config by remember { mutableStateOf(WidgetConfig(style = defaultStyle)) }
                // Al reconfigurar, arranca con lo que el widget ya tenía.
                LaunchedEffect(Unit) { read(appWidgetId)?.let { config = it } }
                ConfigScreen(
                    kind = kind,
                    config = config,
                    onChange = { config = it },
                    snapshots = remember { repository.allCached() },
                    places = remember { repository.places().map { it.id to it.name } },
                    others = others,
                    onCopy = { id ->
                        lifecycleScope.launch {
                            // Se copia todo menos el dato del Indicador, que es propio de ese widget.
                            read(id)?.let { copied -> config = copied.copy(metric = config.metric) }
                        }
                    },
                    onReset = { config = WidgetConfig(style = defaultStyle) },
                    onDone = {
                        lifecycleScope.launch {
                            val context = this@WidgetConfigActivity
                            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                            updateAppWidgetState(context, glanceId) { prefs -> config.writeTo(prefs) }
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
    kind: WidgetKind?,
    config: WidgetConfig,
    onChange: (WidgetConfig) -> Unit,
    snapshots: List<WeatherSnapshot>,
    places: List<Pair<String, String>>,
    others: List<Pair<WidgetKind, Int>>,
    onCopy: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    var sizeIndex by remember { mutableStateOf(1) }
    var backdrop by remember { mutableStateOf(PreviewBackdrop.PHOTO) }
    val previewSnapshot = snapshots.firstOrNull { it.placeId == config.placeId } ?: snapshots.firstOrNull()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        kind?.let { tr("Personalizá", "Customize") + " " + stringResource(it.nameRes) } ?: tr("Personalizá el widget", "Customize the widget"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    TextButton(onClick = onReset) { Text(tr("Restablecer", "Reset")) }
                },
            )
        },
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
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            // --- Vista previa ---
            item {
                val sizes = kind?.sizes
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = (sizes?.large?.height ?: 150.dp) + 40.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .backdrop(backdrop)
                        .animateContentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (kind != null && sizes != null) {
                        GlanceWidgetPreview(kind, sizes.all[sizeIndex], config)
                    } else {
                        StyleThumbnail(config.style, config.accent, config.opacity, previewSnapshot, selected = false, large = true)
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .padding(top = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (kind != null) {
                        listOf(tr("Chico", "Small"), tr("Mediano", "Medium"), tr("Grande", "Large")).forEachIndexed { index, label ->
                            FilterChip(selected = sizeIndex == index, onClick = { sizeIndex = index }, label = { Text(label) })
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    PreviewBackdrop.entries.forEach { option ->
                        FilterChip(selected = backdrop == option, onClick = { backdrop = option }, label = { Text(option.label) })
                    }
                }
            }

            if (kind?.hasMetric == true) {
                item { SectionTitle(tr("Dato", "Data")) }
                item {
                    ChipRow(Metric.entries, config.metric, { it.label }) { onChange(config.copy(metric = it)) }
                }
            }

            // --- Estilo, color y forma ---
            item { SectionTitle(tr("Estilo", "Style")) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    WidgetStyle.entries.forEach { style ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onChange(config.copy(style = style)) }
                                .padding(4.dp),
                        ) {
                            StyleThumbnail(style, config.accent, config.opacity, previewSnapshot, selected = config.style == style)
                            Text(
                                style.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (config.style == style) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            item { SectionTitle(tr("Color", "Color")) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    WidgetAccent.entries.forEach { accent ->
                        AccentOption(accent, selected = config.accent == accent) { onChange(config.copy(accent = accent)) }
                    }
                }
            }
            if (config.style == WidgetStyle.SYSTEM || config.style == WidgetStyle.COLOR) {
                item { SectionTitle(tr("Opacidad del fondo", "Background opacity") + " · ${config.opacity}%") }
                item {
                    Slider(
                        value = config.opacity.toFloat(),
                        onValueChange = { onChange(config.copy(opacity = (it / 5).toInt() * 5)) },
                        valueRange = 0f..100f,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
            item { SectionTitle(tr("Esquinas", "Corners")) }
            item { ChipRow(WidgetCorners.entries, config.corners, { it.label }) { onChange(config.copy(corners = it)) } }

            // --- Contenido y comportamiento ---
            item { SectionTitle(tr("Qué mostrar", "What to show")) }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                    Column {
                        WidgetPart.entries.forEach { part ->
                            ListItem(
                                headlineContent = { Text(part.label) },
                                supportingContent = { Text(part.note) },
                                trailingContent = {
                                    Switch(
                                        checked = part !in config.hidden,
                                        onCheckedChange = { show ->
                                            onChange(config.copy(hidden = if (show) config.hidden - part else config.hidden + part))
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    tr("No todos los widgets tienen todas las partes: cada uno oculta las que muestra.", "Not every widget has every part: each one hides the ones it shows."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            item { SectionTitle(tr("Al tocar el widget", "When you tap the widget")) }
            item { ChipRow(WidgetTap.entries, config.tap, { it.label }) { onChange(config.copy(tap = it)) } }

            // --- Ciudad ---
            item { SectionTitle(tr("Ciudad", "City")) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val here = snapshots.firstOrNull { it.placeId == CURRENT_PLACE_ID }
                    CityOption(
                        title = tr("Donde estoy", "My location"),
                        snapshot = here,
                        icon = true,
                        selected = config.placeId == CURRENT_PLACE_ID,
                    ) { onChange(config.copy(placeId = CURRENT_PLACE_ID)) }
                    places.forEach { (id, name) ->
                        CityOption(
                            title = name,
                            snapshot = snapshots.firstOrNull { it.placeId == id },
                            selected = config.placeId == id,
                        ) { onChange(config.copy(placeId = id)) }
                    }
                    if (places.isNotEmpty()) {
                        CityOption(
                            title = tr("Rotar", "Rotate"),
                            subtitle = tr("Una ciudad distinta cada media hora", "A different city every half hour"),
                            snapshot = null,
                            selected = config.placeId == ROTATE_PLACE_ID,
                        ) { onChange(config.copy(placeId = ROTATE_PLACE_ID)) }
                    }
                }
            }
            if (places.isEmpty()) {
                item {
                    Text(
                        tr("Podés agregar más ciudades desde la app, en Ciudades.", "You can add more cities in the app, under Cities."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }

            // --- Copiar ---
            if (others.isNotEmpty()) {
                item { SectionTitle(tr("Copiar de otro widget", "Copy from another widget")) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Si hay dos del mismo tipo se numeran, para distinguirlos.
                        val counts = mutableMapOf<WidgetKind, Int>()
                        others.forEach { (otherKind, id) ->
                            val number = (counts[otherKind] ?: 0) + 1
                            counts[otherKind] = number
                            val total = others.count { it.first == otherKind }
                            val name = stringResource(otherKind.nameRes) + if (total > 1) " $number" else ""
                            FilterChip(
                                selected = false,
                                onClick = { onCopy(id) },
                                label = { Text(name) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

/** Miniatura del widget en un estilo, con el color y la opacidad elegidos. */
@Composable
private fun StyleThumbnail(
    style: WidgetStyle,
    accent: WidgetAccent,
    opacity: Int,
    snapshot: WeatherSnapshot?,
    selected: Boolean,
    large: Boolean = false,
) {
    val dark = MaterialTheme.colorScheme.surface.luminanceIsDark()
    val schemes = accentSchemes(accent)
    val scheme = schemes?.let { if (dark) it.second else it.first } ?: MaterialTheme.colorScheme
    val alpha = opacity / 100f
    val code = snapshot?.current?.code ?: 2
    val (background, text) = when (style) {
        WidgetStyle.SYSTEM -> scheme.surfaceContainerHigh.copy(alpha = alpha) to scheme.onSurface
        WidgetStyle.COLOR -> scheme.primaryContainer.copy(alpha = alpha) to scheme.onPrimaryContainer
        WidgetStyle.TRANSPARENT, WidgetStyle.SKY -> Color.Transparent to Color.White
    }
    val shape = RoundedCornerShape(if (large) 24.dp else 16.dp)
    Box(
        Modifier
            .size(if (large) 220.dp else 104.dp, if (large) 110.dp else 72.dp)
            .clip(RoundedCornerShape(if (large) 28.dp else 18.dp))
            .backdrop(PreviewBackdrop.PHOTO)
            .then(
                if (selected) {
                    Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(18.dp))
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .padding(8.dp)
                .clip(shape)
                .then(
                    if (style == WidgetStyle.SKY) {
                        Modifier.background(Brush.verticalGradient(skyColors(WeatherCodes.kind(code), snapshot?.current?.isDay ?: true)))
                    } else {
                        Modifier.background(background)
                    },
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(snapshot?.current?.temperature?.degrees() ?: "22°", color = text, fontSize = if (large) 34.sp else 20.sp)
            Spacer(Modifier.width(4.dp))
            Image(
                painter = painterResource(WeatherCodes.icon(code, snapshot?.current?.isDay ?: true)),
                contentDescription = null,
                modifier = Modifier.size(if (large) 48.dp else 26.dp),
            )
        }
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/** Círculo de color; el de fondo de pantalla es un degradado con los colores del tema. */
@Composable
private fun AccentOption(accent: WidgetAccent, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .then(
                    if (accent == WidgetAccent.DYNAMIC) {
                        Modifier.background(Brush.sweepGradient(listOf(colors.primary, colors.tertiary, colors.secondary, colors.primary)))
                    } else {
                        Modifier.background(accent.swatch)
                    },
                )
                .then(if (selected) Modifier.border(3.dp, colors.onSurface, CircleShape) else Modifier)
                .clickable(onClick = onClick)
                .semantics { contentDescription = accent.label },
        )
        Text(
            accent.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Ciudad como tarjeta chica, con su temperatura de ahora. */
@Composable
private fun CityOption(
    title: String,
    snapshot: WeatherSnapshot?,
    selected: Boolean,
    icon: Boolean = false,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            if (snapshot != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(snapshot.current.temperature.degrees(), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(6.dp))
                    Image(
                        painter = painterResource(WeatherCodes.icon(snapshot.current.code, snapshot.current.isDay)),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            } else {
                Text(
                    subtitle ?: tr("Sin datos todavía", "No data yet"),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
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
