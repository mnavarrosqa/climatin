package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.Crossfade
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import com.mnavarrosqa.clima.ui.theme.Appearance
import com.mnavarrosqa.clima.ui.theme.ThemeMode
import com.mnavarrosqa.clima.ui.theme.Accent
import com.mnavarrosqa.clima.ui.theme.HeroLine
import com.mnavarrosqa.clima.ui.theme.HeroSize
import com.mnavarrosqa.clima.ui.theme.SkyMotion
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.degreesDelta
import com.mnavarrosqa.clima.data.windText
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnavarrosqa.clima.data.LocationProvider
import com.mnavarrosqa.clima.data.PressureUnit
import com.mnavarrosqa.clima.data.TemperatureUnit
import com.mnavarrosqa.clima.data.Updates
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WindUnit
import com.mnavarrosqa.clima.wallpaper.LiveWallpaper
import com.mnavarrosqa.clima.wallpaper.WallpaperSettingsActivity
import com.mnavarrosqa.clima.widget.WidgetCatalog
import com.mnavarrosqa.clima.widget.WidgetGalleryActivity
import com.mnavarrosqa.clima.work.AlertNotifier
import com.mnavarrosqa.clima.work.ChangeNotifier
import com.mnavarrosqa.clima.work.HealthNotifier
import com.mnavarrosqa.clima.work.RoutineNotifier
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.work.NotifyPrefs
import com.mnavarrosqa.clima.work.NotifySettings
import com.mnavarrosqa.clima.work.RainNotifier
import com.mnavarrosqa.clima.work.SummaryNotifier
import com.mnavarrosqa.clima.work.SunNotifier
import com.mnavarrosqa.clima.work.Outing
import com.mnavarrosqa.clima.work.LiveUpdate
import com.mnavarrosqa.clima.work.TemperatureNotifier
import androidx.compose.foundation.layout.width
import com.mnavarrosqa.clima.data.shortHour
import kotlin.math.roundToInt

/** Páginas de ajustes. */
private enum class SettingsPage { APPEARANCE, SCREEN, UNITS, NOTIFICATIONS, LOCATION, ABOUT }

/**
 * Ajustes, separados de las ciudades. La página principal muestra las categorías con un resumen
 * de cómo está cada una (como los ajustes del Pixel); cada categoría abre su propia página.
 */
@Composable
fun SettingsScreen(
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    onLocationPermission: (Boolean) -> Unit,
    onShowWelcome: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Plegable abierto o tablet: las categorías a la izquierda y la elegida a la derecha, como
        // los ajustes del sistema. En el teléfono, una pantalla por vez.
        val wide = maxWidth >= SettingsTwoPaneWidth
        // Atrás desde una página vuelve a la lista de categorías, no al pronóstico (con dos paneles
        // la lista ya está a la vista: atrás sale de ajustes).
        BackHandler(enabled = page != null && !wide) { page = null }
        if (wide) {
            val shown = page ?: SettingsPage.APPEARANCE
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(380.dp).fillMaxHeight()) {
                    SettingsHome(viewModel, onBack = onBack, onOpen = { page = it }, selected = shown)
                }
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    CompositionLocalProvider(LocalSettingsPane provides true) {
                        Crossfade(shown, animationSpec = tween(220), label = "pagina") { current ->
                            SettingsPageContent(current, viewModel, onBack = { page = null }, onLocationPermission, onShowWelcome)
                        }
                    }
                }
            }
        } else {
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val forward = targetState != null
                    (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
                },
                label = "ajustes",
            ) { current ->
                if (current == null) {
                    SettingsHome(viewModel, onBack = onBack, onOpen = { page = it })
                } else {
                    SettingsPageContent(current, viewModel, onBack = { page = null }, onLocationPermission, onShowWelcome)
                }
            }
        }
    }
}

/** Ancho desde el que los ajustes van en dos paneles (el razr fold abierto, tablets). */
private val SettingsTwoPaneWidth = 720.dp

/** Verdadero en el panel derecho: la página no lleva flecha de volver, la lista está al lado. */
private val LocalSettingsPane = staticCompositionLocalOf { false }

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    onLocationPermission: (Boolean) -> Unit,
    onShowWelcome: () -> Unit,
) {
    when (page) {
        SettingsPage.APPEARANCE -> SettingsScaffold(tr("Apariencia", "Appearance"), onBack = onBack) { appearancePage() }
        SettingsPage.SCREEN -> SettingsScaffold(tr("Pantalla del clima", "Weather screen"), onBack = onBack) { screenPage(viewModel) }
        SettingsPage.UNITS -> SettingsScaffold(tr("Unidades", "Units"), onBack = onBack) { unitsPage(viewModel) }
        SettingsPage.NOTIFICATIONS -> NotificationsPage(viewModel, onBack = onBack)
        SettingsPage.LOCATION -> LocationPage(onBack = onBack, onLocationPermission = onLocationPermission)
        SettingsPage.ABOUT -> SettingsScaffold(tr("Acerca de", "About"), onBack = onBack) { aboutPage(viewModel, onShowWelcome) }
    }
}

// --- Estructura común ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!LocalSettingsPane.current) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            // En plegables y tablets la lista no se estira de punta a punta.
            modifier = Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp + LocalBottomBarSpace.current,
            ),
            content = content,
        )
    }
}

/**
 * Filas agrupadas como en los ajustes de Android 14+: el grupo tiene las puntas bien redondeadas
 * y entre fila y fila queda una separación fina.
 */
@Composable
private fun Rows(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    nested: Boolean = false,
    /** La categoría abierta en el panel derecho (dos paneles): resaltada en la lista. */
    selected: Boolean = false,
) {
    // Una opción que depende de la de arriba va corrida a la derecha, como en los ajustes del sistema.
    val indent: (@Composable () -> Unit)? = if (nested) ({ Spacer(Modifier.width(8.dp)) }) else null
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { text -> @Composable { Text(text) } },
        leadingContent = icon ?: indent,
        // Con interruptor, toda la fila lo prende y apaga (y TalkBack la lee como un interruptor);
        // antes solo respondía el interruptor chiquito.
        trailingContent = if (checked != null) ({ Switch(checked = checked, onCheckedChange = null) }) else trailing,
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            // Solo la categoría abierta se anuncia como elegida; las demás filas no dicen nada de más.
            .then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
            .then(
                when {
                    checked != null && onCheckedChange != null ->
                        Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    // Sin toque propio, TalkBack lee título, resumen y lo de la derecha juntos, en un solo paso.
                    else -> Modifier.semantics(mergeDescendants = true) { }
                },
            ),
    )
}

/** Ícono de categoría: un círculo de color con el símbolo adentro. */
@Composable
private fun CategoryIcon(background: Color, vector: ImageVector? = null, content: (@Composable () -> Unit)? = null) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (vector != null) Icon(vector, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        content?.invoke()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        // Encabezado: TalkBack permite saltar de sección en sección.
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

private val Chevron: @Composable () -> Unit = {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
}

// --- Página principal ---

@Composable
private fun SettingsHome(
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    selected: SettingsPage? = null,
) {
    val context = LocalContext.current
    val units by viewModel.units.collectAsStateWithLifecycle()
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val rainAlerts by viewModel.rainAlerts.collectAsStateWithLifecycle()
    val smnAlerts by viewModel.smnAlerts.collectAsStateWithLifecycle()
    val statusTemperature by viewModel.statusTemperature.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val installed = remember { Updates.installedVersion(context) }
    var hasLocation by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    var hasBackground by remember { mutableStateOf(LocationProvider.hasBackgroundPermission(context)) }
    var wallpaperActive by remember { mutableStateOf(LiveWallpaper.isActive(context)) }
    var placedWidgets by remember { mutableStateOf(WidgetCatalog.placed(context).size) }
    // Los permisos y widgets cambian fuera de la app: se releen al volver.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocation = LocationProvider.hasPermission(context)
        hasBackground = LocationProvider.hasBackgroundPermission(context)
        wallpaperActive = LiveWallpaper.isActive(context)
        placedWidgets = WidgetCatalog.placed(context).size
    }

    val notifications = listOfNotNull(
        tr("Lluvia", "Rain").takeIf { rainAlerts },
        tr("SMN", "SMN").takeIf { smnAlerts },
        tr("Barra de estado", "Status bar").takeIf { statusTemperature },
        tr("Resumen", "Summary").takeIf { NotifyPrefs.get(context).summary },
        tr("Cambios", "Changes").takeIf { NotifyPrefs.settings.changes },
        tr("Rutina", "Routine").takeIf { NotifyPrefs.settings.routine },
    ).joinToString(" · ").ifEmpty { tr("Apagadas", "Off") }
    val location = when {
        !hasLocation -> tr("Sin permiso: solo ves tus ciudades guardadas", "No permission: saved cities only")
        hasBackground -> tr("Aproximada, todo el tiempo", "Approximate, all the time")
        else -> tr("Aproximada, solo con la app abierta", "Approximate, only while open")
    }

    SettingsScaffold(tr("Ajustes", "Settings"), onBack = onBack) {
        item { SectionTitle(tr("Pronóstico", "Forecast")) }
        item {
            Rows {
                SettingRow(
                    tr("Apariencia", "Appearance"),
                    when (Appearance.mode) {
                        ThemeMode.SYSTEM -> tr("Tema del sistema", "System theme")
                        ThemeMode.LIGHT -> tr("Claro", "Light")
                        ThemeMode.DARK -> tr("Oscuro", "Dark")
                    } + " · " + (if (Appearance.dynamicColors) tr("colores del fondo de pantalla", "wallpaper colors") else Appearance.accent.label.lowercase()) +
                        " · " + tr("ícono", "icon") + " " + AppIcon.current(context).label.lowercase(),
                    icon = {
                        CategoryIcon(Color(0xFF455A64)) {
                            Text("◐", color = Color.White, fontSize = 22.sp)
                        }
                    },
                    onClick = { onOpen(SettingsPage.APPEARANCE) },
                    selected = selected == SettingsPage.APPEARANCE,
                    trailing = Chevron,
                )
                SettingRow(
                    tr("Pantalla del clima", "Weather screen"),
                    tr("${cards.visible.size} de ${cards.order.size} tarjetas · ", "${cards.visible.size} of ${cards.order.size} cards · ") +
                        cards.visible.joinToString(", ") { it.label.lowercase() },
                    icon = { CategoryIcon(Color(0xFF3F7BE8), Icons.Rounded.Home) },
                    onClick = { onOpen(SettingsPage.SCREEN) },
                    selected = selected == SettingsPage.SCREEN,
                    trailing = Chevron,
                )
                SettingRow(
                    tr("Unidades", "Units"),
                    "${units.temperature.label} · ${units.wind.label} · ${units.pressure.label} · " + if (units.use24h) "24 h" else "12 h",
                    icon = {
                        CategoryIcon(Color(0xFF00A0A0)) {
                            Text("°", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                        }
                    },
                    onClick = { onOpen(SettingsPage.UNITS) },
                    selected = selected == SettingsPage.UNITS,
                    trailing = Chevron,
                )
            }
        }
        item { SectionTitle(tr("Avisos y ubicación", "Alerts and location")) }
        item {
            Rows {
                SettingRow(
                    tr("Notificaciones", "Notifications"),
                    notifications,
                    icon = { CategoryIcon(Color(0xFFE8833A), Icons.Rounded.Notifications) },
                    onClick = { onOpen(SettingsPage.NOTIFICATIONS) },
                    selected = selected == SettingsPage.NOTIFICATIONS,
                    trailing = Chevron,
                )
                SettingRow(
                    tr("Ubicación", "Location"),
                    location,
                    icon = { CategoryIcon(Color(0xFF2E9E5B), Icons.Rounded.LocationOn) },
                    onClick = { onOpen(SettingsPage.LOCATION) },
                    selected = selected == SettingsPage.LOCATION,
                    trailing = Chevron,
                )
            }
        }
        item { SectionTitle(tr("Personalización", "Personalization")) }
        item {
            Rows {
                SettingRow(
                    tr("Fondo de pantalla", "Wallpaper"),
                    if (wallpaperActive) tr("Activo · tocá para cambiar diseño y opciones", "In use · tap to change design and options") else tr("Cielo, paisaje, formas u oscuro", "Sky, landscape, shapes or dark"),
                    icon = {
                        CategoryIcon(Color(0xFF7E57C2)) {
                            Image(painterResource(WeatherCodes.icon(2)), contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    },
                    onClick = { context.startActivity(Intent(context, WallpaperSettingsActivity::class.java)) },
                    trailing = Chevron,
                )
                SettingRow(
                    tr("Widgets", "Widgets"),
                    when (placedWidgets) {
                        0 -> WidgetCatalog.kinds.size.let { tr("$it widgets para tu pantalla de inicio", "$it widgets for your home screen") }
                        1 -> tr("1 puesto · agregar o personalizar", "1 placed · add or customize")
                        else -> tr("$placedWidgets puestos · agregar o personalizar", "$placedWidgets placed · add or customize")
                    },
                    icon = {
                        CategoryIcon(Color(0xFFD9488B)) {
                            Image(painterResource(WeatherCodes.icon(0)), contentDescription = null, modifier = Modifier.size(28.dp))
                        }
                    },
                    onClick = { context.startActivity(Intent(context, WidgetGalleryActivity::class.java)) },
                    trailing = Chevron,
                )
            }
        }
        item { SectionTitle(tr("Climatin", "Climatin")) }
        item {
            Rows {
                SettingRow(
                    tr("Acerca de y actualizaciones", "About and updates"),
                    update?.let { tr("Hay una versión nueva: ${it.versionName}", "New version available: ${it.versionName}") }
                        ?: tr("Versión ${installed.first}", "Version ${installed.first}"),
                    icon = { CategoryIcon(Color(0xFF607D8B), Icons.Rounded.Info) },
                    onClick = { onOpen(SettingsPage.ABOUT) },
                    selected = selected == SettingsPage.ABOUT,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (update != null) Badge { Text("1") }
                            Chevron()
                        }
                    },
                )
            }
        }
    }
}

// --- Apariencia ---

private fun LazyListScope.appearancePage() {
    item { SectionTitle(tr("Tema", "Theme")) }
    item {
        val context = LocalContext.current
        Rows {
            ThemeMode.entries.forEach { mode ->
                SettingRow(
                    when (mode) {
                        ThemeMode.SYSTEM -> tr("Como el sistema", "Follow system")
                        ThemeMode.LIGHT -> tr("Claro", "Light")
                        ThemeMode.DARK -> tr("Oscuro", "Dark")
                    },
                    when (mode) {
                        ThemeMode.SYSTEM -> tr("Cambia solo cuando el teléfono pasa a modo oscuro", "Switches when your phone goes dark")
                        ThemeMode.LIGHT -> tr("Tarjetas y ajustes claros siempre", "Light cards and settings, always")
                        ThemeMode.DARK -> tr("Tarjetas y ajustes oscuros siempre", "Dark cards and settings, always")
                    },
                    onClick = { Appearance.setMode(context, mode) },
                    // Un solo lugar para tocar: la fila. El botón solo muestra cuál está elegido.
                    trailing = { RadioButton(selected = Appearance.mode == mode, onClick = null) },
                )
            }
        }
    }
    item {
        Hint(tr("El cielo del pronóstico no cambia con el tema: sigue al clima y a la hora del lugar.", "The forecast sky doesn't change with the theme: it follows the weather and local time."))
    }
    item { SectionTitle(tr("Color de acento", "Accent color")) }
    item {
        val context = LocalContext.current
        // "Del fondo de pantalla" es Material You: solo desde Android 12.
        val accents = Accent.entries.filter { it != Accent.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        val selected = if (Appearance.dynamicColors || Appearance.accent != Accent.WALLPAPER) Appearance.accent else Accent.BLUE
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    accents.forEach { accent ->
                        Swatch(
                            label = accent.label,
                            selected = accent == selected,
                            onClick = { Appearance.setAccent(context, accent) },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        if (accent == Accent.WALLPAPER) {
                                            Brush.sweepGradient(listOf(Color(0xFF3A5F90), Color(0xFF2E6B3A), Color(0xFF8F4C00), Color(0xFF9C3D6A), Color(0xFF3A5F90)))
                                        } else {
                                            SolidColor(accent.swatch)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
    item {
        Hint(tr("Botones, interruptores y resaltados. Los widgets tienen su propio color en su personalización.", "Buttons, switches and highlights. Widgets have their own color in their settings."))
    }
    item { SectionTitle(tr("Ícono de la app", "App icon")) }
    item {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val current = AppIcon.current(context)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppIcon.entries.forEach { icon ->
                    Swatch(
                        label = icon.label,
                        selected = icon == current,
                        dimension = 64.dp,
                        onClick = {
                            if (icon != current) scope.launch { AppIcon.set(context, icon) }
                        },
                    ) {
                        Image(painterResource(icon.background), contentDescription = null, modifier = Modifier.fillMaxSize())
                        // El primer plano de un ícono adaptable tiene margen: se agranda para que
                        // se vea como en la pantalla de inicio.
                        Image(
                            painterResource(icon.foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.45f
                                    scaleY = 1.45f
                                },
                        )
                    }
                }
            }
        }
    }
    item {
        Hint(tr("El cambio puede tardar unos segundos. Algunas pantallas de inicio sacan el ícono viejo y hay que volver a ponerlo.", "It may take a few seconds. Some home screens remove the old icon and you'll need to add it again."))
    }
}

/** Opción redonda con su nombre abajo: color de acento o ícono de la app. */
@Composable
private fun Swatch(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    dimension: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit,
) {
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, label = "anillo")
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .widthIn(min = dimension + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(dimension + 10.dp)
                .border(ring, MaterialTheme.colorScheme.primary, CircleShape)
                .padding(5.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            content()
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Pantalla del clima ---

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.screenPage(viewModel: WeatherViewModel) {
    item { SectionTitle(tr("Temperatura", "Temperature")) }
    item {
        val context = LocalContext.current
        Rows {
            ChoiceRow(tr("Tamaño del número", "Number size"), HeroSize.entries, Appearance.heroSize, { it.label }) {
                Appearance.setHeroSize(context, it)
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(4.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(tr("Debajo de la temperatura", "Below the temperature"), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroLine.entries.forEach { line ->
                            val shown = line in Appearance.heroLines
                            FilterChip(
                                selected = shown,
                                onClick = { Appearance.setHeroLine(context, line, !shown) },
                                label = { Text(line.label) },
                            )
                        }
                    }
                }
            }
        }
    }
    item { SectionTitle(tr("Diseño", "Layout")) }
    item {
        val context = LocalContext.current
        Rows {
            SettingRow(
                tr("Pronóstico compacto", "Compact forecast"),
                tr("Menos espacio entre tarjetas, encabezado más bajo y una semana de días. En pantallas bajas se activa solo", "Less space between cards, a shorter header and one week of days. Turns on by itself on short screens"),
                checked = Appearance.compact,
                onCheckedChange = { Appearance.setCompact(context, it) },
            )
            ChoiceRow(tr("Cielo animado", "Animated sky"), SkyMotion.entries, Appearance.skyMotion, { it.label }) {
                Appearance.setSkyMotion(context, it)
            }
        }
    }
    item {
        Hint(tr("Reducido mueve el cielo, la lluvia y la nieve más despacio; quieto lo deja fijo y gasta menos batería.", "Reduced moves the sky, rain and snow more slowly; still keeps it fixed and saves battery."))
    }
    item { SectionTitle(tr("Tarjetas", "Cards")) }
    item { Hint(tr("Elegí qué tarjetas ves en el pronóstico y en qué orden.", "Choose which cards you see in the forecast and in what order.")) }
    item {
        val layout by viewModel.cards.collectAsStateWithLifecycle()
        Rows {
            layout.order.forEachIndexed { index, card ->
                SettingRow(
                    card.label,
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.setCards(layout.move(card, -1)) }, enabled = index > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = tr("Subir", "Move up"))
                            }
                            IconButton(onClick = { viewModel.setCards(layout.move(card, 1)) }, enabled = index < layout.order.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = tr("Bajar", "Move down"))
                            }
                            Switch(
                                checked = card !in layout.hidden,
                                onCheckedChange = { viewModel.setCards(layout.toggle(card, it)) },
                            )
                        }
                    },
                )
            }
        }
    }
    item {
        Hint(tr("En plegables y tablets, el mapa y los detalles van en el panel izquierdo.", "On foldables and tablets, the map and details go in the left pane."))
    }
}

// --- Unidades ---

private fun LazyListScope.unitsPage(viewModel: WeatherViewModel) {
    item {
        val units by viewModel.units.collectAsStateWithLifecycle()
        Rows {
            ChoiceRow(tr("Temperatura", "Temperature"), TemperatureUnit.entries, units.temperature, { it.label }) {
                viewModel.setUnits(units.copy(temperature = it))
            }
            ChoiceRow(tr("Viento", "Wind"), WindUnit.entries, units.wind, { it.label }) {
                viewModel.setUnits(units.copy(wind = it))
            }
            ChoiceRow(tr("Presión", "Pressure"), PressureUnit.entries, units.pressure, { it.label }) {
                viewModel.setUnits(units.copy(pressure = it))
            }
            SettingRow(
                tr("Formato de 24 horas", "24-hour time"),
                if (units.use24h) "18:30" else "6:30 PM",
                checked = units.use24h,
                onCheckedChange = { viewModel.setUnits(units.copy(use24h = it)) },
            )
        }
    }
    item { Hint(tr("Los widgets y las notificaciones usan las mismas unidades.", "Widgets and notifications use the same units.")) }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, labelOf: (T) -> String, onSelect: (T) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(6.dp))
            Row(
                // Una sola opción a la vez: TalkBack lo anuncia como grupo.
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = { Text(labelOf(option)) },
                        // FilterChip se anuncia como casilla; la semántica de afuera la pisa: botón de opción.
                        modifier = Modifier.semantics {
                            role = Role.RadioButton
                            this.selected = option == selected
                        },
                    )
                }
            }
        }
    }
}

// --- Notificaciones ---

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun NotificationsPage(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val rainAlerts by viewModel.rainAlerts.collectAsStateWithLifecycle()
    val smnAlerts by viewModel.smnAlerts.collectAsStateWithLifecycle()
    val statusTemperature by viewModel.statusTemperature.collectAsStateWithLifecycle()
    var canNotify by remember { mutableStateOf(RainNotifier.canNotify(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canNotify = RainNotifier.canNotify(context) }
    // Qué aviso pidió el permiso, para activarlo cuando lo den.
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        when (pending) {
            "smn" -> viewModel.setSmnAlerts(granted)
            "temperatura" -> viewModel.setStatusTemperature(granted)
            "resumen" -> {
                NotifyPrefs.update(context) { it.copy(summary = granted) }
                SummaryNotifier.schedule(context)
            }
            "cambios" -> NotifyPrefs.update(context) { it.copy(changes = granted) }
            "sol" -> NotifyPrefs.update(context) { it.copy(goldenHour = granted) }
            "rutina" -> {
                NotifyPrefs.update(context) { it.copy(routine = granted) }
                RoutineNotifier.schedule(context)
            }
            "uv" -> NotifyPrefs.update(context) { it.copy(uvAlert = granted) }
            "aire" -> NotifyPrefs.update(context) { it.copy(airAlert = granted) }
            else -> viewModel.setRainAlerts(granted)
        }
        pending = null
    }
    fun toggle(kind: String, enabled: Boolean, set: (Boolean) -> Unit) {
        if (enabled && !canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pending = kind
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            set(enabled)
        }
    }

    // Carga los ajustes guardados (la primera vez) y los lee como estado: si un botón de una
    // notificación los cambia, esta página se actualiza sola.
    NotifyPrefs.get(context)
    val notify = NotifyPrefs.settings
    fun change(transform: (NotifySettings) -> NotifySettings) = NotifyPrefs.update(context, transform)

    val places by viewModel.places.collectAsStateWithLifecycle()

    // Qué hora se está eligiendo: la del resumen, la de la rutina o el principio o el final del silencio.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    when (picking) {
        "salida" -> HourPickerDialog(tr("Salgo a las", "I leave at"), notify.routineLeave, onDismiss = { picking = null }) { hour ->
            change { it.copy(routineLeave = hour) }
            RoutineNotifier.schedule(context)
            picking = null
        }
        "vuelta" -> HourPickerDialog(tr("Vuelvo a las", "I'm back at"), notify.routineBack, onDismiss = { picking = null }) { hour ->
            change { it.copy(routineBack = hour) }
            picking = null
        }
        "resumen" -> HourPickerDialog(tr("Resumen a las", "Summary at"), notify.summaryHour, onDismiss = { picking = null }) { hour ->
            change { it.copy(summaryHour = hour) }
            SummaryNotifier.schedule(context)
            picking = null
        }
        "desde" -> HourPickerDialog(tr("Silencio desde", "Quiet from"), notify.quietStart, onDismiss = { picking = null }) { hour ->
            change { it.copy(quietStart = hour) }
            picking = null
        }
        "hasta" -> HourPickerDialog(tr("Silencio hasta", "Quiet until"), notify.quietEnd, onDismiss = { picking = null }) { hour ->
            change { it.copy(quietEnd = hour) }
            picking = null
        }
    }

    // Los avisos de prueba van en un diálogo: antes eran ocho botones en una tira que había que deslizar.
    var testing by rememberSaveable { mutableStateOf(false) }
    if (testing) {
        val tests = listOf(
            tr("Lluvia cerca", "Rain nearby") to { RainNotifier.test(context) },
            tr("Alerta oficial", "Official alert") to { AlertNotifier.test(context) },
            tr("Resumen del día", "Daily summary") to { SummaryNotifier.notify(context, test = true) },
            tr("Cambio brusco", "Big change") to { ChangeNotifier.test(context) },
            tr("Hora dorada", "Golden hour") to { SunNotifier.test(context) },
            tr("Mi rutina", "My routine") to { RoutineNotifier.test(context) },
            tr("UV muy alto", "Very high UV") to { HealthNotifier.testUv(context) },
            tr("Calidad del aire", "Air quality") to { HealthNotifier.testAir(context) },
        )
        AlertDialog(
            onDismissRequest = { testing = false },
            title = { Text(tr("Probar avisos", "Try alerts")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        tr("Tocá uno para mandar un ejemplo y ver cómo queda.", "Tap one to send a sample and see how it looks."),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    // El diálogo queda abierto para probar varios seguidos.
                    Rows {
                        tests.forEach { (label, send) ->
                            SettingRow(
                                label,
                                onClick = { if (canNotify) send() },
                                trailing = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { testing = false }) { Text(tr("Listo", "Done")) } },
        )
    }

    SettingsScaffold(tr("Notificaciones", "Notifications"), onBack = onBack) {
        if (!canNotify) {
            item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(tr("Las notificaciones de Climatin están apagadas", "Climatin notifications are off"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            tr("Al activar un aviso te las pido; si ya dijiste que no, se prenden desde los ajustes del sistema.", "Turning on an alert asks for them; if you already said no, enable them in system settings."),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { openAppNotificationSettings(context) }) { Text(tr("Abrir ajustes del sistema", "Open system settings")) }
                    }
                }
            }
        }
        if (System.currentTimeMillis() < notify.mutedUntil) {
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("Avisos pausados hasta mañana (salvo alertas rojas)", "Alerts paused until tomorrow (except red alerts)"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { change { it.copy(mutedUntil = 0L) } }) { Text(tr("Reanudar", "Resume")) }
                    }
                }
            }
        }

        // El silencio vale para todos los avisos, así que va arriba y no dentro de un grupo.
        item { SectionTitle(tr("Horario de silencio", "Quiet hours")) }
        item {
            Rows {
                SettingRow(
                    tr("No avisar de noche", "No alerts at night"),
                    tr("Las alertas rojas del SMN avisan igual", "Red official alerts still come through"),
                    checked = notify.quiet,
                    onCheckedChange = { on -> change { it.copy(quiet = on) } },
                )
                if (notify.quiet) {
                    SettingRow(tr("Desde", "From"), hourLabel(notify.quietStart), onClick = { picking = "desde" }, trailing = Chevron)
                    SettingRow(tr("Hasta", "Until"), hourLabel(notify.quietEnd), onClick = { picking = "hasta" }, trailing = Chevron)
                }
            }
        }

        // --- Lluvia y alertas: lo que avisa cuando pasa algo ---
        item { SectionTitle(tr("Lluvia y alertas", "Rain and alerts")) }
        item {
            Rows {
                SettingRow(
                    tr("Lluvia cerca", "Rain nearby"),
                    tr("Cuándo empieza, cuánto dura y qué tan fuerte", "When it starts, how long and how heavy"),
                    checked = rainAlerts && canNotify,
                    onCheckedChange = { toggle("lluvia", it, viewModel::setRainAlerts) },
                )
                if (rainAlerts) {
                    ChoiceRow(tr("Avisar con anticipación de", "Warn ahead by"), listOf(15, 30, 60), notify.rainLead, { "$it min" }) { lead ->
                        change { it.copy(rainLead = lead) }
                    }
                    SettingRow(
                        tr("Cuando para de llover", "When rain stops"),
                        tr("\"Para de llover en unos 15 min\"", "\"Rain stops in about 15 min\""),
                        checked = notify.rainStops,
                        onCheckedChange = { on -> change { it.copy(rainStops = on) } },
                    )
                    if (LiveUpdate.supported) {
                        SettingRow(
                            tr("En vivo en la barra de estado", "Live in the status bar"),
                            tr("\"12 min\" hasta que empieza y \"Para 25 min\" mientras llueve", "\"12 min\" until it starts and \"Stops 25 min\" while raining"),
                            checked = notify.liveRain,
                            onCheckedChange = { on -> change { it.copy(liveRain = on) } },
                            nested = true,
                        )
                    }
                }
            }
        }
        groupGap()
        item {
            Rows {
                SettingRow(
                    tr("Alertas del SMN", "Official alerts (Argentina)"),
                    tr("Con su nivel (amarilla, naranja o roja) y aviso cuando terminan", "With their level (yellow, orange or red) and a notice when they end"),
                    checked = smnAlerts && canNotify,
                    onCheckedChange = { toggle("smn", it, viewModel::setSmnAlerts) },
                )
                if (smnAlerts && LiveUpdate.supported) {
                    SettingRow(
                        tr("Naranjas y rojas, en vivo", "Orange and red, live"),
                        tr("En la barra de estado mientras están vigentes, con cuánto falta para que terminen", "In the status bar while active, with how long until they end"),
                        checked = notify.liveAlerts,
                        onCheckedChange = { on -> change { it.copy(liveAlerts = on) } },
                        nested = true,
                    )
                }
            }
        }
        groupGap()
        item {
            Rows {
                SettingRow(
                    tr("Avisar cambios bruscos", "Alert on big changes"),
                    tr("Una vez por día cada uno, en un horario que tenga sentido", "Once a day each, at a sensible time"),
                    checked = notify.changes && canNotify,
                    onCheckedChange = { on ->
                            toggle("cambios", on) { enabled -> change { it.copy(changes = enabled) } }
                        },
                )
                if (notify.changes) {
                    SettingRow(
                        tr("Bajón o subida de temperatura", "Temperature swing"),
                        6.0.degreesDelta().let { tr("Mañana $it o más distinto que hoy", "Tomorrow $it or more different from today") },
                        checked = notify.changeTemperature,
                        onCheckedChange = { on -> change { it.copy(changeTemperature = on) } },
                    )
                    SettingRow(
                        tr("Heladas", "Frost"),
                        1.0.degrees().let { tr("Mínima de $it o menos, avisado a la tarde", "Low of $it or less, in the afternoon") },
                        checked = notify.changeFrost,
                        onCheckedChange = { on -> change { it.copy(changeFrost = on) } },
                    )
                    SettingRow(
                        tr("Viento fuerte", "Strong wind"),
                        windText(50.0).let { tr("$it o más", "$it or more") },
                        checked = notify.changeWind,
                        onCheckedChange = { on -> change { it.copy(changeWind = on) } },
                    )
                }
            }
        }
        groupGap()
        item {
            Rows {
                SettingRow(
                    tr("UV muy alto", "Very high UV"),
                    tr("Índice 8 o más, a la mañana y con el horario: \"UV muy alto de 11 a 16\"", "Index 8 or more, in the morning with the hours: \"Very high UV from 11 to 4\""),
                    checked = notify.uvAlert && canNotify,
                    onCheckedChange = { on -> toggle("uv", on) { enabled -> change { it.copy(uvAlert = enabled) } } },
                )
                SettingRow(
                    tr("Calidad del aire mala", "Poor air quality"),
                    tr("Cuando está mala o va a empeorar, una vez por día", "When it's poor or getting worse, once a day"),
                    checked = notify.airAlert && canNotify,
                    onCheckedChange = { on -> toggle("aire", on) { enabled -> change { it.copy(airAlert = enabled) } } },
                )
            }
        }

        // --- Resúmenes y rutina: lo que llega a una hora fija ---
        item { SectionTitle(tr("Resúmenes y rutina", "Summaries and routine")) }
        item {
            Rows {
                SettingRow(
                    tr("Resumen del día", "Daily summary"),
                    tr("Máxima, mínima, lluvia, UV y viento. Desde las 17, el de mañana", "High, low, rain, UV and wind. From 5 PM, tomorrow's"),
                    checked = notify.summary && canNotify,
                    onCheckedChange = { on ->
                            toggle("resumen", on) { enabled ->
                                change { it.copy(summary = enabled) }
                                SummaryNotifier.schedule(context)
                            }
                        },
                )
                if (notify.summary) {
                    SettingRow(
                        tr("A las", "At"),
                        hourLabel(notify.summaryHour),
                        onClick = { picking = "resumen" },
                        trailing = Chevron,
                    )
                    // Con ciudades guardadas se puede elegir de cuál; si la elegida se borró, queda "Mi ubicación".
                    if (places.isNotEmpty()) {
                        val options = listOf(CURRENT_PLACE_ID) + places.map { it.id }
                        val selected = notify.summaryPlace.takeIf { it in options } ?: CURRENT_PLACE_ID
                        ChoiceRow(
                            tr("De", "For"),
                            options,
                            selected,
                            { id -> places.firstOrNull { it.id == id }?.shownName ?: tr("Mi ubicación", "My location") },
                        ) { id -> change { it.copy(summaryPlace = id) } }
                    }
                }
            }
        }
        groupGap()
        item {
            Rows {
                SettingRow(
                    tr("Aviso antes de salir", "Heads-up before leaving"),
                    tr("Una hora antes: el clima de la ida y de la vuelta, y si hace falta paraguas o abrigo", "An hour before: the weather going and coming back, and whether you need an umbrella or a jacket"),
                    checked = notify.routine && canNotify,
                    onCheckedChange = { on ->
                        toggle("rutina", on) { enabled ->
                            change { it.copy(routine = enabled) }
                            RoutineNotifier.schedule(context)
                        }
                    },
                )
                if (notify.routine) {
                    SettingRow(tr("Salgo a las", "I leave at"), hourLabel(notify.routineLeave), onClick = { picking = "salida" }, trailing = Chevron)
                    SettingRow(
                        tr("Vuelvo a las", "I'm back at"),
                        hourLabel(notify.routineBack) + if (notify.routineBack <= notify.routineLeave) tr(" del día siguiente", " the next day") else "",
                        onClick = { picking = "vuelta" },
                        trailing = Chevron,
                    )
                    SettingRow(
                        tr("Solo días de semana", "Weekdays only"),
                        tr("De lunes a viernes", "Monday to Friday"),
                        checked = notify.routineWeekdays,
                        onCheckedChange = { on ->
                            change { it.copy(routineWeekdays = on) }
                            RoutineNotifier.schedule(context)
                        },
                    )
                }
            }
        }
        groupGap()
        item {
            Rows {
                SettingRow(
                    tr("Hora dorada", "Golden hour"),
                    tr("Media hora antes de que salga o se ponga el sol, con cuenta regresiva", "Half an hour before sunrise or sunset, with a countdown"),
                    checked = notify.goldenHour && canNotify,
                    onCheckedChange = { on ->
                            toggle("sol", on) { enabled ->
                                change { it.copy(goldenHour = enabled) }
                                if (enabled) SunNotifier.check(context)
                            }
                        },
                )
            }
        }

        // --- Barra de estado: lo que queda fijo arriba. Junta lo que antes era "Siempre a la vista"
        // y "En vivo en la barra de estado", que hablaban de lo mismo. ---
        item { SectionTitle(tr("Barra de estado", "Status bar")) }
        item {
            Rows {
                SettingRow(
                    tr("Temperatura en la barra de estado", "Temperature in the status bar"),
                    tr("Los grados al lado de la hora; al expandirla, las próximas horas", "The temperature next to the clock; expand it for the next hours"),
                    checked = statusTemperature && canNotify,
                    onCheckedChange = { toggle("temperatura", it, viewModel::setStatusTemperature) },
                )
                // Depende de la temperatura en la barra: sin ella no hay notificación que subir a chip.
                if (statusTemperature && LiveUpdate.supported) {
                    SettingRow(
                        tr("Como chip en vivo", "As a live chip"),
                        12.0.degrees().let { tr("Los grados de ahora y lo que viene (↓ $it, ☔ 16:00)", "The temperature now and what comes next (↓ $it, ☔ 4 PM)") },
                        checked = notify.liveTemperature,
                        onCheckedChange = { on ->
                            change { it.copy(liveTemperature = on) }
                            TemperatureNotifier.update(context)
                        },
                        nested = true,
                    )
                }
                if (LiveUpdate.supported) {
                    SettingRow(
                        tr("Permiso de Live Updates", "Live Updates permission"),
                        tr("Si no aparece el chip, revisá que esté permitido", "If the chip doesn't show, check that it's allowed"),
                        onClick = { openAppNotificationSettings(context) },
                        trailing = Chevron,
                    )
                }
            }
        }
        if (LiveUpdate.supported) {
            item {
                Hint(
                    tr(
                        "Android 16 puede mostrar la lluvia, las alertas y la temperatura como un chip al lado de la hora y arriba en la pantalla de bloqueo (Live Updates). Se eligen en cada aviso; el sistema decide cuándo.",
                        "Android 16 can show rain, alerts and the temperature as a chip next to the clock and at the top of the lock screen (Live Updates). Choose them under each alert; the system decides when.",
                    ),
                )
            }
        } else {
            groupGap()
        }
        // "Salgo ahora" también vive en la barra de estado: dos horas de clima en vivo.
        item {
            val active = Outing.active(context) && Outing.endsAt > 0
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (active) {
                                tr("Siguiendo el clima hasta las ", "Tracking weather until ") +
                                    shortHour(java.time.Instant.ofEpochMilli(Outing.endsAt).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
                            } else {
                                tr("Salgo ahora: dos horas de clima en vivo", "Heading out: two hours of live weather")
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            tr("Temperatura, lluvia y viento de donde estás, actualizados cada 15 min. También desde ajustes rápidos o manteniendo apretado el ícono.", "Temperature, rain and wind where you are, updated every 15 min. Also from Quick Settings or by long-pressing the icon."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        enabled = canNotify || active,
                        onClick = { if (active) Outing.stop(context) else Outing.start(context) },
                    ) { Text(if (active) tr("Terminar", "Stop") else tr("Empezar", "Start")) }
                }
            }
        }

        // Probar y los ajustes del sistema van al final, separados de los grupos: no son avisos.
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Rows {
                SettingRow(
                    tr("Probar avisos", "Try alerts"),
                    if (canNotify) {
                        tr("Mandá un aviso de ejemplo para ver cómo queda", "Send a sample to see how it looks")
                    } else {
                        tr("Primero activá las notificaciones", "Turn on notifications first")
                    },
                    // Sin permiso no sale nada, así que la fila no abre el diálogo (como antes los botones apagados).
                    onClick = if (canNotify) ({ testing = true }) else null,
                    trailing = if (canNotify) Chevron else null,
                )
                SettingRow(
                    tr("Más opciones del sistema", "More system options"),
                    tr("Sonido, vibración y cómo se muestra cada tipo de aviso", "Sound, vibration and how each kind of alert shows up"),
                    onClick = { openAppNotificationSettings(context) },
                    trailing = Chevron,
                )
            }
        }
    }
}

/** Separación entre tarjetas de un mismo grupo, más chica que la de un título de sección. */
private fun LazyListScope.groupGap() {
    item { Spacer(Modifier.height(12.dp)) }
}

// --- Ubicación ---

@Composable
private fun LocationPage(onBack: () -> Unit, onLocationPermission: (Boolean) -> Unit) {
    val context = LocalContext.current
    var hasLocation by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    var hasBackground by remember { mutableStateOf(LocationProvider.hasBackgroundPermission(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocation = LocationProvider.hasPermission(context)
        hasBackground = LocationProvider.hasBackgroundPermission(context)
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasLocation = it
        onLocationPermission(it)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasBackground = it }

    SettingsScaffold(tr("Ubicación", "Location"), onBack = onBack) {
        item {
            Hint(
                tr(
                    "Climatin usa tu ubicación aproximada (unos kilómetros), nunca la exacta, y solo para pedir el pronóstico.",
                    "Climatin uses your approximate location (a few kilometers), never the exact one, only to fetch the forecast.",
                ),
            )
        }
        item {
            Rows {
                SettingRow(
                    tr("Ubicación aproximada", "Approximate location"),
                    if (hasLocation) tr("Permitida: el pronóstico sigue donde estás", "Allowed: the forecast follows you") else tr("Sin permiso: solo ves tus ciudades guardadas", "Not allowed: you only see saved cities"),
                    trailing = if (hasLocation) null else {
                        { FilledTonalButton(onClick = { LocationAsk.request(context, locationLauncher) }) { Text(tr("Permitir", "Allow")) } }
                    },
                )
                SettingRow(
                    tr("Con la app cerrada", "While closed"),
                    if (hasBackground && hasLocation) {
                        tr("Los widgets y los avisos te siguen aunque no abras la app", "Widgets and alerts follow you even when the app is closed")
                    } else {
                        tr("Elegí \"Permitir todo el tiempo\" para que los widgets te sigan", "Choose \"Allow all the time\" so widgets follow you")
                    },
                    trailing = if (hasBackground && hasLocation) null else {
                        {
                            FilledTonalButton(
                                enabled = hasLocation,
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                },
                            ) { Text(tr("Permitir", "Allow")) }
                        }
                    },
                )
            }
        }
        item {
            Rows {
                SettingRow(
                    tr("Permisos en los ajustes del sistema", "Permissions in system settings"),
                    tr("Para cambiar o quitar el permiso", "To change or remove the permission"),
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }
                    },
                    trailing = Chevron,
                )
            }
        }
    }
}

// --- Acerca de ---

private fun LazyListScope.aboutPage(viewModel: WeatherViewModel, onShowWelcome: () -> Unit) {
    item {
        val context = LocalContext.current
        val update by viewModel.update.collectAsStateWithLifecycle()
        val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
        val updateMessage by viewModel.updateMessage.collectAsStateWithLifecycle()
        val installed = remember { Updates.installedVersion(context) }
        val fromPlay = remember { Updates.installedFromPlay(context) }
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(WeatherCodes.icon(2)), contentDescription = null, modifier = Modifier.size(72.dp))
                Text("Climatin", style = MaterialTheme.typography.headlineMedium)
                Text(tr("Versión ${installed.first} (${installed.second})", "Version ${installed.first} (${installed.second})"), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                val release = update
                val progress = updateProgress
                when {
                    fromPlay -> Text(tr("Se actualiza desde Google Play", "Updated through Google Play"), style = MaterialTheme.typography.bodyMedium)
                    progress != null -> {
                        WavyProgress(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(tr("Bajando… ${(progress * 100).roundToInt()}%", "Downloading… ${(progress * 100).roundToInt()}%"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    }
                    release != null -> {
                        Text(tr("Hay una versión nueva: ${release.versionName}", "New version available: ${release.versionName}"), style = MaterialTheme.typography.titleSmall)
                        release.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(onClick = { viewModel.downloadUpdate(release) }) { Text(tr("Actualizar", "Update")) }
                    }
                    else -> {
                        updateMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        FilledTonalButton(onClick = viewModel::checkForUpdates) { Text(tr("Buscar actualizaciones", "Check for updates")) }
                    }
                }
            }
        }
    }
    item { SectionTitle(tr("Compartir", "Share")) }
    item {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        Rows {
            SettingRow(
                tr("Mandar la app", "Send the app"),
                tr("El instalador, por Quick Share o un chat. Funciona sin conexión", "The installer, over Quick Share or a chat. Works offline"),
                onClick = {
                    scope.launch {
                        runCatching { AppShare.shareApk(context) }.onFailure {
                            android.widget.Toast.makeText(context, tr("No pude preparar el instalador", "Couldn't prepare the installer"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                trailing = Chevron,
            )
            SettingRow(
                tr("Compartir el link", "Share the link"),
                tr("Para bajar la última versión", "To download the latest version"),
                onClick = { runCatching { AppShare.shareLink(context) } },
                trailing = Chevron,
            )
        }
    }
    item { SectionTitle(tr("Ayuda", "Help")) }
    item {
        Rows {
            SettingRow(
                tr("Ver la bienvenida", "Show the welcome"),
                tr("Los primeros pasos y los gestos de la app", "First steps and the app's gestures"),
                onClick = onShowWelcome,
                trailing = Chevron,
            )
        }
    }
    item { SectionTitle(tr("Datos", "Data")) }
    item {
        val context = LocalContext.current
        fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        Rows {
            SettingRow(tr("Pronóstico y calidad del aire", "Forecast and air quality"), "Open-Meteo", onClick = { open("https://open-meteo.com") }, trailing = Chevron)
            SettingRow(tr("Alertas oficiales", "Official alerts"), tr("Servicio Meteorológico Nacional", "Argentina's National Weather Service"), onClick = { open("https://www.smn.gob.ar") }, trailing = Chevron)
            SettingRow(tr("Mapa base", "Base map"), "Esri", onClick = { open("https://www.esri.com") }, trailing = Chevron)
            SettingRow(tr("Código fuente", "Source code"), "github.com/mnavarrosqa/climatin", onClick = { open("https://github.com/mnavarrosqa/climatin") }, trailing = Chevron)
        }
    }
}
