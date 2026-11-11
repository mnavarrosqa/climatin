package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import com.mnavarrosqa.clima.ui.ClimaSectionTitle
import com.mnavarrosqa.clima.ui.ClimaShapes
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
        // Un id que no es de un widget de Climatin (otra app o un widget ya quitado): no hay nada que editar.
        val kind = WidgetCatalog.kindOf(this, appWidgetId) ?: run {
            finish()
            return
        }
        val defaultStyle = kind?.defaultStyle ?: WidgetStyle.SYSTEM
        val others = WidgetCatalog.placed(this).filter { it.second != appWidgetId }

        // Lo guardado en un widget y si alguna vez se personalizó (al guardar siempre queda el estilo).
        suspend fun read(id: Int): Pair<WidgetConfig, Boolean>? = runCatching {
            val glanceId = GlanceAppWidgetManager(this).getGlanceIdBy(id)
            val prefs = getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
            // Cada widget con su propio estilo por defecto: copiar de un Vistazo sin configurar
            // tiene que dar Transparente, no el del widget que se está editando.
            val sourceDefault = WidgetCatalog.kindOf(this, id)?.defaultStyle ?: WidgetStyle.SYSTEM
            WidgetConfig.from(prefs, sourceDefault, repository::resolvePlaceId) to (prefs[WidgetPrefs.STYLE] != null)
        }.getOrNull()

        // Android 12+ no abre esta pantalla al agregar (la configuración es opcional): ahí siempre
        // se está editando un widget ya puesto. Antes, el launcher la abre para terminar de agregarlo.
        val addingFlow = intent?.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S

        setContent {
            ClimaTheme {
                // Guardado con rememberSaveable: al plegar o girar el teléfono la pantalla se arma
                // de nuevo y, sin esto, se perdía lo elegido.
                var config by rememberSaveable(stateSaver = ConfigSaver) { mutableStateOf(WidgetConfig(style = defaultStyle)) }
                // Lo que el widget tiene guardado: contra esto se sabe si hay cambios sin guardar.
                var stored by rememberSaveable(stateSaver = ConfigSaver) { mutableStateOf(WidgetConfig(style = defaultStyle)) }
                var loaded by rememberSaveable { mutableStateOf(false) }
                var firstTime by rememberSaveable { mutableStateOf(false) }
                // Al reconfigurar, arranca con lo que el widget ya tenía (una sola vez: después de
                // plegar o girar no hay que pisar lo que se estaba eligiendo).
                LaunchedEffect(Unit) {
                    if (!loaded) {
                        read(appWidgetId)?.let { (saved, configured) ->
                            config = saved
                            stored = saved
                            firstTime = addingFlow && !configured
                        }
                        loaded = true
                    }
                }
                ConfigScreen(
                    kind = kind,
                    config = config,
                    firstTime = firstTime,
                    dirty = config != stored,
                    onClose = ::finish,
                    onChange = { config = it },
                    snapshots = remember { repository.allCached() },
                    places = remember { repository.places().map { it.id to it.shownName } },
                    others = others,
                    onCopy = { id ->
                        lifecycleScope.launch {
                            // Se copia todo menos el dato del Indicador, que es propio de ese widget.
                            read(id)?.let { (copied, _) -> config = copied.copy(metric = config.metric) }
                        }
                    },
                    onReset = { config = WidgetConfig(style = defaultStyle) },
                    onDone = {
                        lifecycleScope.launch {
                            val context = this@WidgetConfigActivity
                            // Si el widget se quitó mientras se editaba, getGlanceIdBy falla: se cierra sin más.
                            runCatching {
                                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                                updateAppWidgetState(context, glanceId) { prefs -> config.writeTo(prefs) }
                                setResult(RESULT_OK, result)
                            }
                            runCatching { updateWeatherWidgets(context) }
                            finish()
                        }
                    },
                )
            }
        }
    }
}

/** Desde este ancho, la vista previa va a la izquierda y las opciones a la derecha. */
private val TwoPaneWidth = 720.dp

/** Con menos alto que esto (teléfono acostado), la vista previa fija no deja lugar para las opciones. */
private val PinnedPreviewMinHeight = 520.dp

/** Para que lo elegido sobreviva a plegar o girar el teléfono. */
private val ConfigSaver = listSaver<WidgetConfig, Any>(
    save = { listOf(it.placeId, it.style.name, it.metric.name, it.accent.name, it.opacity, it.corners.name, it.tap.name, hiddenRaw(it.hidden)) },
    restore = {
        WidgetConfig(
            placeId = it[0] as String,
            style = WidgetStyle.from(it[1] as String),
            metric = Metric.from(it[2] as String),
            accent = WidgetAccent.from(it[3] as String),
            opacity = it[4] as Int,
            corners = WidgetCorners.from(it[5] as String),
            tap = WidgetTap.from(it[6] as String),
            hidden = hiddenParts(it[7] as String),
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    kind: WidgetKind?,
    config: WidgetConfig,
    /** Se está terminando de agregar el widget (no editando uno ya puesto). */
    firstTime: Boolean,
    /** Hay cambios sin guardar. */
    dirty: Boolean,
    onClose: () -> Unit,
    onChange: (WidgetConfig) -> Unit,
    snapshots: List<WeatherSnapshot>,
    places: List<Pair<String, String>>,
    others: List<Pair<WidgetKind, Int>>,
    onCopy: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    var sizeIndex by rememberSaveable { mutableStateOf(1) }
    var backdrop by rememberSaveable { mutableStateOf(PreviewBackdrop.PHOTO) }
    val previewSnapshot = snapshots.firstOrNull { it.placeId == config.placeId } ?: snapshots.firstOrNull()
    val previewSize = kind?.sizes?.all?.get(sizeIndex)

    // Salir con cambios sin guardar pide confirmación: antes "atrás" los descartaba sin avisar.
    var confirmDiscard by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (dirty) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(enabled = dirty && !confirmDiscard) { confirmDiscard = true }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(tr("¿Descartar los cambios?", "Discard changes?")) },
            text = { Text(tr("El widget queda como estaba.", "The widget stays as it was.")) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onClose()
                }) { Text(tr("Descartar", "Discard")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(tr("Seguir editando", "Keep editing")) }
            },
        )
    }

    // Las opciones son las mismas en una columna o en dos paneles: se arman una vez.
    val options: LazyListScope.() -> Unit = {
        // --- Ciudad --- (lo primero que se elige; Ciudades muestra todas y no la usa)
        if (kind?.supportsCity != false) {
            item { SectionTitle(tr("Ciudad", "City")) }
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val here = snapshots.firstOrNull { it.placeId == CURRENT_PLACE_ID }
                    CityOption(
                        title = tr("Mi ubicación", "My location"),
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
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WidgetStyle.entries.forEach { style ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(ClimaShapes.tile)
                            .selectable(
                                selected = config.style == style,
                                role = Role.RadioButton,
                                onClick = { onChange(config.copy(style = style)) },
                            )
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
        // Transparente y Cielo no tienen fondo de color: el color no cambiaba nada.
        val tinted = config.style == WidgetStyle.SYSTEM || config.style == WidgetStyle.COLOR
        if (tinted) item { SectionTitle(tr("Color", "Color")) }
        if (tinted) item {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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
        // Sin fondo no hay esquinas que redondear, y la Burbuja tiene su propia forma.
        if (config.style != WidgetStyle.TRANSPARENT && kind?.receiver != BurbujaWidgetReceiver::class.java) {
            item { SectionTitle(tr("Esquinas", "Corners")) }
            item { ChipRow(WidgetCorners.entries, config.corners, { it.label }) { onChange(config.copy(corners = it)) } }
        }

        // --- Contenido y comportamiento ---
        // Solo las partes que este widget dibuja: las demás no cambiaban nada.
        val parts = kind?.parts?.let { shown -> WidgetPart.entries.filter { it in shown } } ?: WidgetPart.entries
        if (parts.isNotEmpty()) {
            item { SectionTitle(tr("Qué mostrar", "What to show")) }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = ClimaShapes.tile) {
                    Column {
                        parts.forEach { part ->
                            val shown = part !in config.hidden
                            ListItem(
                                headlineContent = { Text(part.label) },
                                supportingContent = { Text(part.note) },
                                // Toda la fila prende y apaga; el interruptor solo muestra el estado.
                                trailingContent = { Switch(checked = shown, onCheckedChange = null) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                modifier = Modifier.toggleable(
                                    value = shown,
                                    role = Role.Switch,
                                    onValueChange = { show ->
                                        onChange(config.copy(hidden = if (show) config.hidden - part else config.hidden + part))
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
        if (kind?.supportsTap != false) {
            // "Siguiente ciudad" no hace nada sin otra ciudad a la cual pasar.
            val taps = WidgetTap.entries.filter { it != WidgetTap.NEXT || (kind?.supportsCity != false && places.isNotEmpty()) }
            item { SectionTitle(tr("Al tocar el widget", "When you tap the widget")) }
            item { ChipRow(taps, config.tap, { it.label }) { onChange(config.copy(tap = it)) } }
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
                        // Sin ícono: el de copiar no viene en material-icons-core, y el de
                        // actualizar daba a entender otra cosa.
                        AssistChip(onClick = { onCopy(id) }, label = { Text(name) })
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            // Barra chica: la grande se llevaba unos 150 dp que ahora usa la vista previa.
            TopAppBar(
                title = {
                    Text(
                        kind?.let { tr("Personalizá", "Customize") + " " + stringResource(it.nameRes) } ?: tr("Personalizá el widget", "Customize the widget"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("Cerrar", "Close"))
                    }
                },
                actions = {
                    TextButton(onClick = onReset) { Text(tr("Restablecer", "Reset")) }
                },
            )
        },
        bottomBar = {
            // Con fondo: sin él, las opciones pasaban por detrás del botón y se leían encimadas.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(onClick = onDone, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                        Text(if (firstTime) tr("Agregar widget", "Add widget") else tr("Guardar", "Save"))
                    }
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val paneHeight = maxHeight
            val controls: @Composable (Modifier) -> Unit = { modifier ->
                PreviewControls(
                    showSizes = kind != null,
                    sizeIndex = sizeIndex,
                    onSize = { sizeIndex = it },
                    backdrop = backdrop,
                    onBackdrop = { backdrop = it },
                    modifier = modifier,
                )
            }
            when {
                // Ancho (tablet o plegable abierto): la vista previa con sus controles a la
                // izquierda, siempre a la vista, y las opciones a la derecha.
                maxWidth >= TwoPaneWidth -> Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 8.dp, bottom = 16.dp),
                    ) {
                        PreviewBox(kind, previewSize, config, backdrop, previewSnapshot, heightLimit = (paneHeight - 150.dp).coerceAtLeast(160.dp))
                        controls(Modifier.padding(top = 12.dp))
                    }
                    LazyColumn(
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                        contentPadding = PaddingValues(start = 8.dp, end = 16.dp, bottom = 16.dp),
                        content = options,
                    )
                }
                // Teléfono: la vista previa queda fija arriba y lo demás se desplaza por debajo,
                // así cada cambio se ve en el widget sin volver al principio.
                paneHeight >= PinnedPreviewMinHeight -> Column(Modifier.fillMaxSize()) {
                    PreviewBox(
                        kind, previewSize, config, backdrop, previewSnapshot,
                        heightLimit = paneHeight * 0.42f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                    ) {
                        item { controls(Modifier) }
                        options(this)
                    }
                }
                // Poco alto (teléfono acostado): todo en una lista, como antes.
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                ) {
                    item { PreviewBox(kind, previewSize, config, backdrop, previewSnapshot, heightLimit = Dp.Infinity) }
                    item { controls(Modifier.padding(top = 12.dp)) }
                    options(this)
                }
            }
        }
    }
}

/**
 * El widget real sobre un fondo de prueba. Si no entra en el ancho o en [heightLimit], se achica
 * entero (no se recorta): el Pronóstico grande mide 300 dp y fijo arriba se comía la pantalla.
 */
@Composable
private fun PreviewBox(
    kind: WidgetKind?,
    size: DpSize?,
    config: WidgetConfig,
    backdrop: PreviewBackdrop,
    snapshot: WeatherSnapshot?,
    heightLimit: Dp,
    modifier: Modifier = Modifier,
) {
    val margin = 16.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(ClimaShapes.card)
            .backdrop(backdrop)
            .animateContentSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (kind != null && size != null) {
            val scale = minOf(1f, (maxWidth - margin * 2) / size.width, (heightLimit - margin * 2) / size.height).coerceAtLeast(0.3f)
            Box(
                Modifier.padding(vertical = margin).size(size.width * scale, size.height * scale),
                contentAlignment = Alignment.Center,
            ) {
                // Se arma al tamaño real del widget y se achica al dibujarlo: así el diseño es
                // el mismo que va a tener en la pantalla de inicio.
                GlanceWidgetPreview(
                    kind,
                    size,
                    config,
                    modifier = Modifier.requiredSize(size).graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                ) {
                    StyleThumbnail(config.style, config.accent, config.opacity, snapshot, selected = false, large = true)
                }
            }
        } else {
            Box(Modifier.padding(vertical = margin)) {
                StyleThumbnail(config.style, config.accent, config.opacity, snapshot, selected = false, large = true)
            }
        }
    }
}

/** Tamaño y fondo de prueba de la vista previa, cada uno en su fila con su nombre. */
@Composable
private fun PreviewControls(
    showSizes: Boolean,
    sizeIndex: Int,
    onSize: (Int) -> Unit,
    backdrop: PreviewBackdrop,
    onBackdrop: (PreviewBackdrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (showSizes) {
            LabelledChips(tr("Tamaño", "Size")) {
                listOf(tr("Chico", "Small"), tr("Mediano", "Medium"), tr("Grande", "Large")).forEachIndexed { index, label ->
                    FilterChip(
                        selected = sizeIndex == index,
                        onClick = { onSize(index) },
                        label = { Text(label) },
                        modifier = Modifier.radioSemantics(sizeIndex == index),
                    )
                }
            }
        }
        LabelledChips(tr("Fondo", "Backdrop")) {
            PreviewBackdrop.entries.forEach { option ->
                FilterChip(
                    selected = backdrop == option,
                    onClick = { onBackdrop(option) },
                    label = { Text(option.label) },
                    modifier = Modifier.radioSemantics(backdrop == option),
                )
            }
        }
    }
}

/** Una fila de chips de opción única con su nombre adelante. */
@Composable
private fun LabelledChips(label: String, chips: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp).widthIn(min = 60.dp),
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            chips()
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    // Una sola opción a la vez: TalkBack la lee como botón de opción, no como casilla.
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = Modifier.radioSemantics(option == selected),
            )
        }
    }
}

/**
 * Un chip de opción única. FilterChip se anuncia como casilla; la semántica de afuera pisa la del
 * chip, así que se lee como botón de opción, elegido o no.
 */
private fun Modifier.radioSemantics(isSelected: Boolean): Modifier = semantics {
    role = Role.RadioButton
    selected = isSelected
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
    // El widget de mentira por dentro y el recuadro de fondo por fuera, con las esquinas de la app.
    val shape = if (large) ClimaShapes.tile else ClimaShapes.inner
    val outer = if (large) ClimaShapes.card else ClimaShapes.inner
    Box(
        Modifier
            .size(if (large) 220.dp else 104.dp, if (large) 110.dp else 72.dp)
            .clip(outer)
            .backdrop(PreviewBackdrop.PHOTO)
            .then(
                if (selected) {
                    Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), outer)
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
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
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
        shape = ClimaShapes.tile,
        modifier = Modifier
            .width(150.dp)
            .clip(ClimaShapes.tile)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
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

/** El título de sección de la app, con el aire de esta lista. */
@Composable
private fun SectionTitle(text: String) {
    // Encabezado: TalkBack permite saltar de sección en sección.
    ClimaSectionTitle(text, Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp).semantics { heading() })
}
