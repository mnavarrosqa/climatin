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
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // Atrás desde una página vuelve a la lista de categorías, no al pronóstico.
    BackHandler(enabled = page != null) { page = null }
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val forward = targetState != null
            (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(tween(320)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
        },
        label = "ajustes",
    ) { current ->
        when (current) {
            null -> SettingsHome(viewModel, onBack = onBack, onOpen = { page = it })
            SettingsPage.APPEARANCE -> SettingsScaffold(tr("Apariencia", "Appearance"), onBack = { page = null }) { appearancePage() }
            SettingsPage.SCREEN -> SettingsScaffold(tr("Pantalla del clima", "Weather screen"), onBack = { page = null }) { screenPage(viewModel) }
            SettingsPage.UNITS -> SettingsScaffold(tr("Unidades", "Units"), onBack = { page = null }) { unitsPage(viewModel) }
            SettingsPage.NOTIFICATIONS -> NotificationsPage(viewModel, onBack = { page = null })
            SettingsPage.LOCATION -> LocationPage(onBack = { page = null }, onLocationPermission = onLocationPermission)
            SettingsPage.ABOUT -> SettingsScaffold(tr("Acerca de", "About"), onBack = { page = null }) { aboutPage(viewModel) }
        }
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
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
                bottom = padding.calculateBottomPadding() + 24.dp,
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
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { text -> @Composable { Text(text) } },
        leadingContent = icon,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            // Sin toque propio, TalkBack lee título, resumen e interruptor juntos, en un solo paso.
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) { }),
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
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
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
private fun SettingsHome(viewModel: WeatherViewModel, onBack: () -> Unit, onOpen: (SettingsPage) -> Unit) {
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
    ).joinToString(" · ").ifEmpty { tr("Apagadas", "Off") }
    val location = when {
        !hasLocation -> tr("Sin permiso: usás solo ciudades guardadas", "No permission: saved cities only")
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
                    trailing = Chevron,
                )
                SettingRow(
                    tr("Pantalla del clima", "Weather screen"),
                    tr("${cards.visible.size} de ${cards.order.size} tarjetas · ", "${cards.visible.size} of ${cards.order.size} cards · ") +
                        cards.visible.joinToString(", ") { it.label.lowercase() },
                    icon = { CategoryIcon(Color(0xFF3F7BE8), Icons.Rounded.Home) },
                    onClick = { onOpen(SettingsPage.SCREEN) },
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
                    trailing = Chevron,
                )
                SettingRow(
                    tr("Ubicación", "Location"),
                    location,
                    icon = { CategoryIcon(Color(0xFF2E9E5B), Icons.Rounded.LocationOn) },
                    onClick = { onOpen(SettingsPage.LOCATION) },
                    trailing = Chevron,
                )
            }
        }
        item { SectionTitle(tr("Personalización", "Personalization")) }
        item {
            Rows {
                SettingRow(
                    tr("Fondo de pantalla", "Wallpaper"),
                    if (wallpaperActive) tr("Puesto · tocá para cambiar diseño y opciones", "In use · tap to change design and options") else tr("Cielo, paisaje, formas u oscuro", "Sky, landscape, shapes or dark"),
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
                        0 -> tr("11 widgets para tu pantalla de inicio", "11 widgets for your home screen")
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
                    trailing = { RadioButton(selected = Appearance.mode == mode, onClick = { Appearance.setMode(context, mode) }) },
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
                tr("Menos espacio entre tarjetas, encabezado más bajo y una semana de días", "Less space between cards, a shorter header and one week of days"),
                onClick = { Appearance.setCompact(context, !Appearance.compact) },
                trailing = { Switch(checked = Appearance.compact, onCheckedChange = { Appearance.setCompact(context, it) }) },
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
                trailing = {
                    Switch(checked = units.use24h, onCheckedChange = { viewModel.setUnits(units.copy(use24h = it)) })
                },
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
                    FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(labelOf(option)) })
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

        item { SectionTitle(tr("Lluvia", "Rain")) }
        item {
            Rows {
                SettingRow(
                    tr("Lluvia cerca", "Rain nearby"),
                    tr("Cuándo empieza, cuánto dura y qué tan fuerte", "When it starts, how long and how heavy"),
                    trailing = { Switch(checked = rainAlerts && canNotify, onCheckedChange = { toggle("lluvia", it, viewModel::setRainAlerts) }) },
                )
                if (rainAlerts) {
                    ChoiceRow(tr("Avisar con anticipación de", "Warn ahead by"), listOf(15, 30, 60), notify.rainLead, { "$it min" }) { lead ->
                        change { it.copy(rainLead = lead) }
                    }
                    SettingRow(
                        tr("Cuando para de llover", "When rain stops"),
                        tr("\"Para de llover en unos 15 min\"", "\"Rain stops in about 15 min\""),
                        trailing = { Switch(checked = notify.rainStops, onCheckedChange = { on -> change { it.copy(rainStops = on) } }) },
                    )
                }
            }
        }

        item { SectionTitle(tr("Alertas oficiales", "Official alerts")) }
        item {
            Rows {
                SettingRow(
                    tr("Alertas del SMN", "Official alerts (Argentina)"),
                    tr("Con su nivel (amarilla, naranja o roja) y aviso cuando terminan", "With their level (yellow, orange or red) and a notice when they end"),
                    trailing = { Switch(checked = smnAlerts && canNotify, onCheckedChange = { toggle("smn", it, viewModel::setSmnAlerts) }) },
                )
            }
        }

        item { SectionTitle(tr("Resumen diario", "Daily summary")) }
        item {
            Rows {
                SettingRow(
                    tr("Resumen del día", "Daily summary"),
                    tr("Máxima, mínima, lluvia, UV y viento. Desde las 17, el de mañana", "High, low, rain, UV and wind. From 5 PM, tomorrow's"),
                    trailing = {
                        Switch(checked = notify.summary && canNotify, onCheckedChange = { on ->
                            toggle("resumen", on) { enabled ->
                                change { it.copy(summary = enabled) }
                                SummaryNotifier.schedule(context)
                            }
                        })
                    },
                )
                if (notify.summary) {
                    ChoiceRow(tr("A las", "At"), listOf(6, 7, 8, 9, 18, 20, 21), notify.summaryHour, { hourLabel(it) }) { hour ->
                        change { it.copy(summaryHour = hour) }
                        SummaryNotifier.schedule(context)
                    }
                }
            }
        }

        item { SectionTitle(tr("Cambios bruscos", "Big changes")) }
        item {
            Rows {
                SettingRow(
                    tr("Avisar cambios bruscos", "Alert on big changes"),
                    tr("Una vez por día cada uno, en un horario que tenga sentido", "Once a day each, at a sensible time"),
                    trailing = {
                        Switch(checked = notify.changes && canNotify, onCheckedChange = { on ->
                            toggle("cambios", on) { enabled -> change { it.copy(changes = enabled) } }
                        })
                    },
                )
                if (notify.changes) {
                    SettingRow(
                        tr("Bajón o subida de temperatura", "Temperature swing"),
                        tr("Mañana 6° o más distinto que hoy", "Tomorrow 6° or more different from today"),
                        trailing = { Switch(checked = notify.changeTemperature, onCheckedChange = { on -> change { it.copy(changeTemperature = on) } }) },
                    )
                    SettingRow(
                        tr("Heladas", "Frost"),
                        tr("Mínima de 1° o menos, avisado a la tarde", "Low of 1° or less, in the afternoon"),
                        trailing = { Switch(checked = notify.changeFrost, onCheckedChange = { on -> change { it.copy(changeFrost = on) } }) },
                    )
                    SettingRow(
                        tr("Viento fuerte", "Strong wind"),
                        tr("50 km/h o más", "50 km/h or more"),
                        trailing = { Switch(checked = notify.changeWind, onCheckedChange = { on -> change { it.copy(changeWind = on) } }) },
                    )
                    SettingRow(
                        tr("UV muy alto", "Very high UV"),
                        tr("Índice 8 o más, avisado a la mañana", "Index 8 or more, in the morning"),
                        trailing = { Switch(checked = notify.changeUv, onCheckedChange = { on -> change { it.copy(changeUv = on) } }) },
                    )
                }
            }
        }

        item { SectionTitle(tr("Siempre a la vista", "Always visible")) }
        item {
            Rows {
                SettingRow(
                    tr("Temperatura en la barra de estado", "Temperature in the status bar"),
                    tr("Los grados al lado de la hora; al expandirla, las próximas horas", "The temperature next to the clock; expand it for the next hours"),
                    trailing = { Switch(checked = statusTemperature && canNotify, onCheckedChange = { toggle("temperatura", it, viewModel::setStatusTemperature) }) },
                )
            }
        }

        item { SectionTitle(tr("Salgo ahora", "Heading out")) }
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
                                tr("Dos horas de clima en vivo", "Two hours of live weather")
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

        item { SectionTitle(tr("Amanecer y atardecer", "Sunrise and sunset")) }
        item {
            Rows {
                SettingRow(
                    tr("Hora dorada", "Golden hour"),
                    tr("Media hora antes de que salga o se ponga el sol, con cuenta regresiva", "Half an hour before sunrise or sunset, with a countdown"),
                    trailing = {
                        Switch(checked = notify.goldenHour && canNotify, onCheckedChange = { on ->
                            toggle("sol", on) { enabled ->
                                change { it.copy(goldenHour = enabled) }
                                if (enabled) SunNotifier.check(context)
                            }
                        })
                    },
                )
            }
        }

        if (LiveUpdate.supported) {
            item { SectionTitle(tr("En vivo en la barra de estado", "Live in the status bar")) }
            item {
                Hint(
                    tr(
                        "Android 16 puede mostrar algunos avisos como un chip al lado de la hora y arriba en la pantalla de bloqueo (Live Updates). El sistema decide cuándo; se puede apagar en los ajustes de la app.",
                        "Android 16 can show some alerts as a chip next to the clock and at the top of the lock screen (Live Updates). The system decides when; you can turn it off in the app's settings.",
                    ),
                )
            }
            item {
                Rows {
                    SettingRow(
                        tr("Lluvia en camino", "Rain on the way"),
                        tr("\"12 min\" hasta que empieza y \"Para 25 min\" mientras llueve", "\"12 min\" until it starts and \"Stops 25 min\" while raining"),
                        trailing = { Switch(checked = notify.liveRain, onCheckedChange = { on -> change { it.copy(liveRain = on) } }) },
                    )
                    SettingRow(
                        tr("Alertas naranjas y rojas", "Orange and red alerts"),
                        tr("Mientras están vigentes, con cuánto falta para que terminen", "While active, with how long until they end"),
                        trailing = { Switch(checked = notify.liveAlerts, onCheckedChange = { on -> change { it.copy(liveAlerts = on) } }) },
                    )
                    SettingRow(
                        tr("Temperatura", "Temperature"),
                        tr("Los grados de ahora y lo que viene (↓ 12°, ☔ 16:00). Necesita la temperatura en la barra de estado prendida", "The temperature now and what comes next (↓ 12°, ☔ 4 PM). Needs the status bar temperature on"),
                        trailing = {
                            Switch(checked = notify.liveTemperature, onCheckedChange = { on ->
                                change { it.copy(liveTemperature = on) }
                                TemperatureNotifier.update(context)
                            })
                        },
                    )
                    SettingRow(
                        tr("Permiso de Live Updates", "Live Updates permission"),
                        tr("Si no aparece el chip, revisá que esté permitido", "If the chip doesn't show, check that it's allowed"),
                        onClick = { openAppNotificationSettings(context) },
                        trailing = Chevron,
                    )
                }
            }
        }

        item { SectionTitle(tr("Horario de silencio", "Quiet hours")) }
        item {
            Rows {
                SettingRow(
                    tr("No avisar de noche", "No alerts at night"),
                    tr("Las alertas rojas del SMN avisan igual", "Red official alerts still come through"),
                    trailing = { Switch(checked = notify.quiet, onCheckedChange = { on -> change { it.copy(quiet = on) } }) },
                )
                if (notify.quiet) {
                    ChoiceRow(tr("Desde", "From"), listOf(21, 22, 23, 0), notify.quietStart, { hourLabel(it) }) { hour ->
                        change { it.copy(quietStart = hour) }
                    }
                    ChoiceRow(tr("Hasta", "Until"), listOf(6, 7, 8, 9), notify.quietEnd, { hourLabel(it) }) { hour ->
                        change { it.copy(quietEnd = hour) }
                    }
                }
            }
        }

        item { SectionTitle(tr("Probar", "Try it")) }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        tr("Mandá un aviso de ejemplo para ver cómo queda.", "Send a sample to see how it looks."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            tr("Lluvia", "Rain") to { RainNotifier.test(context) },
                            tr("Alerta", "Alert") to { AlertNotifier.test(context) },
                            tr("Resumen", "Summary") to { SummaryNotifier.notify(context, test = true) },
                            tr("Cambio brusco", "Big change") to { ChangeNotifier.test(context) },
                            tr("Hora dorada", "Golden hour") to { SunNotifier.test(context) },
                        ).forEach { (label, send) ->
                            FilledTonalButton(
                                enabled = canNotify,
                                onClick = { send() },
                            ) { Text(label) }
                        }
                    }
                }
            }
        }
        item {
            Rows {
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

/** "7:00" o "7 AM" según el formato elegido en Unidades. */
private fun hourLabel(hour: Int): String = shortHour(java.time.LocalDate.now().atTime(hour, 0))

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
                        { FilledTonalButton(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text(tr("Permitir", "Allow")) } }
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

private fun LazyListScope.aboutPage(viewModel: WeatherViewModel) {
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
