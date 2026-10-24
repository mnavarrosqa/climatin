package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlin.math.absoluteValue
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnavarrosqa.clima.data.AppRelease
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Day
import com.mnavarrosqa.clima.data.LocationProvider
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.Moon
import com.mnavarrosqa.clima.data.WeatherAlert
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.longDate
import com.mnavarrosqa.clima.data.pressureText
import com.mnavarrosqa.clima.data.timeOf
import com.mnavarrosqa.clima.data.updatedAt
import com.mnavarrosqa.clima.data.windText
import kotlin.math.roundToInt

private val OnSky = Color.White
private val OnSkyVariant = Color.White.copy(alpha = 0.85f)

/** Destino de un acceso directo desde un widget: una ciudad y, opcionalmente, un día (yyyy-MM-dd). */
data class DeepLinkTarget(val placeId: String, val day: String?)

@Composable
fun WeatherRoute(
    deepLink: DeepLinkTarget? = null,
    onDeepLinkHandled: () -> Unit = {},
    viewModel: WeatherViewModel = viewModel(),
) {
    val context = LocalContext.current
    val places by viewModel.places.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    var showCities by rememberSaveable { mutableStateOf(false) }
    // "lat,lon" del radar abierto, o null.
    var radarAt by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.refresh()
    }

    LaunchedEffect(hasPermission, places.size) { viewModel.refreshIfStale() }
    // Un acceso directo siempre lleva al pronóstico, aunque estés en otra pantalla.
    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            showCities = false
            radarAt = null
        }
    }
    BackHandler(enabled = showCities || radarAt != null) {
        if (radarAt != null) radarAt = null else showCities = false
    }

    val pages = buildList {
        if (hasPermission || viewModel.cached(CURRENT_PLACE_ID) != null) add(CURRENT_PLACE_ID)
        places.forEach { add(it.id) }
    }

    val radar = radarAt?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 }
    val screen = when {
        radar != null -> "radar"
        showCities -> "ciudades"
        pages.isEmpty() -> "permiso"
        else -> "clima"
    }
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(380, easing = EaseOutCubic), initialScale = 0.94f))
                .togetherWith(fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 1.03f))
        },
        label = "pantalla",
    ) { target ->
        when (target) {
            "radar" -> radar?.let { RadarScreen(it[0], it[1], onBack = { radarAt = null }) }
            "ciudades" -> CitiesScreen(
                viewModel = viewModel,
                onBack = { showCities = false },
                onLocationPermission = { granted ->
                    hasPermission = granted
                    if (granted) viewModel.refresh()
                },
            )
            "permiso" -> PermissionScreen(
                onAllow = { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                onPickCity = { showCities = true },
            )
            // Al cambiar las unidades se rearma el pronóstico para que todos los textos se reformateen.
            else -> key(units) {
                WeatherPager(
                    pages,
                    viewModel,
                    onOpenCities = { showCities = true },
                    onOpenRadar = { lat, lon -> radarAt = "$lat,$lon" },
                    deepLink = deepLink,
                    onDeepLinkHandled = onDeepLinkHandled,
                )
            }
        }
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onPickCity: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedSky(WeatherKind.PARTLY, isDay = true, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(WeatherCodes.icon(2)),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                tr("El clima donde estés", "The weather wherever you are"),
                style = MaterialTheme.typography.headlineMedium,
                color = OnSky,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                tr("Necesito tu ubicación aproximada para mostrarte el pronóstico y mantener los widgets al día.", "I need your approximate location to show the forecast and keep the widgets up to date."),
                style = MaterialTheme.typography.bodyLarge,
                color = OnSkyVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onAllow) { Text(tr("Permitir ubicación", "Allow location")) }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onPickCity) { Text(tr("Elegir una ciudad", "Pick a city")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherPager(
    pages: List<String>,
    viewModel: WeatherViewModel,
    onOpenCities: () -> Unit,
    onOpenRadar: (Double, Double) -> Unit,
    deepLink: DeepLinkTarget?,
    onDeepLinkHandled: () -> Unit,
) {
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pagerState = rememberPagerState { pages.size }
    // Día a abrir en la página de destino del acceso directo.
    var pendingDay by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        val index = pages.indexOf(target.placeId).takeIf { it >= 0 } ?: 0
        pagerState.animateScrollToPage(index)
        pendingDay = target.day?.let { pages[index] to it }
        onDeepLinkHandled()
    }
    val skyId = pages.getOrElse(pagerState.currentPage) { CURRENT_PLACE_ID }
    val skySnapshot by remember(skyId) { viewModel.snapshots(skyId) }
        .collectAsStateWithLifecycle(viewModel.cached(skyId))

    Box(Modifier.fillMaxSize()) {
        AnimatedSky(
            kind = skySnapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY,
            isDay = skySnapshot?.current?.isDay ?: true,
            modifier = Modifier.fillMaxSize(),
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(
                    Modifier.graphicsLayer {
                        // La página que se va se achica y se apaga un poco: da profundidad al deslizar.
                        val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        alpha = 1f - 0.45f * offset
                        val scale = 1f - 0.08f * offset
                        scaleX = scale
                        scaleY = scale
                    },
                ) {
                    WeatherPage(
                        placeId = pages[page],
                        viewModel = viewModel,
                        error = error,
                        onOpenRadar = onOpenRadar,
                        openDay = pendingDay?.takeIf { it.first == pages[page] }?.second,
                        onDayOpened = { pendingDay = null },
                        banner = update?.let { release ->
                            @Composable {
                                UpdateBanner(
                                    release,
                                    onUpdate = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)))
                                    },
                                    onDismiss = viewModel::dismissUpdate,
                                )
                            }
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(48.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        if (pages.size > 1) {
                            contentDescription = tr(
                                "Ciudad ${pagerState.currentPage + 1} de ${pages.size}",
                                "City ${pagerState.currentPage + 1} of ${pages.size}",
                            )
                        }
                    },
                horizontalArrangement = Arrangement.Center,
            ) {
                if (pages.size > 1) {
                    pages.indices.forEach { index ->
                        val selected = index == pagerState.currentPage
                        val width by animateDpAsState(if (selected) 20.dp else 6.dp, Motion.snappy(), label = "punto")
                        val dotAlpha by animateFloatAsState(if (selected) 1f else 0.45f, label = "punto alfa")
                        Box(
                            Modifier
                                .padding(horizontal = 3.dp)
                                .size(width = width, height = 6.dp)
                                .clip(CircleShape)
                                .background(OnSky.copy(alpha = dotAlpha)),
                        )
                    }
                }
            }
            IconButton(onClick = onOpenCities) {
                Icon(Icons.Rounded.Menu, contentDescription = tr("Ciudades y ajustes", "Cities and settings"), tint = OnSky)
            }
        }
    }
}

@Composable
private fun WeatherPage(
    placeId: String,
    viewModel: WeatherViewModel,
    error: String?,
    onOpenRadar: (Double, Double) -> Unit,
    openDay: String? = null,
    onDayOpened: () -> Unit = {},
    banner: (@Composable () -> Unit)? = null,
) {
    val snapshot by remember(placeId) { viewModel.snapshots(placeId) }
        .collectAsStateWithLifecycle(viewModel.cached(placeId))
    val current = snapshot
    var selectedDay by remember { mutableStateOf<Day?>(null) }
    val shownAt = remember { SystemClock.uptimeMillis() }
    if (current != null) {
        selectedDay?.let { day -> DaySheet(current, day, onDismiss = { selectedDay = null }) }
    }
    LaunchedEffect(openDay, current != null) {
        val date = openDay ?: return@LaunchedEffect
        val day = current?.days?.firstOrNull { it.date == date } ?: return@LaunchedEffect
        selectedDay = day
        onDayOpened()
    }

    ForecastContent(
        snapshot = current,
        isCurrentLocation = placeId == CURRENT_PLACE_ID,
        error = error,
        onDay = { selectedDay = it },
        onOpenRadar = onOpenRadar,
        shownAt = shownAt,
        banner = banner,
    )
}

/** Ancho desde el que el pronóstico se divide en dos paneles (plegables abiertos y tablets). */
private val TwoPaneWidth = 600.dp

/**
 * El pronóstico de un lugar. En pantallas angostas va en una columna; en anchas, en dos paneles:
 * el cielo, la temperatura y los avisos a la izquierda, y horas, días y detalles a la derecha.
 * No depende del ViewModel, así se puede capturar en los tests de pantalla.
 */
@Composable
fun ForecastContent(
    snapshot: WeatherSnapshot?,
    isCurrentLocation: Boolean,
    error: String?,
    onDay: (Day) -> Unit,
    onOpenRadar: (Double, Double) -> Unit,
    shownAt: Long = 0L,
    banner: (@Composable () -> Unit)? = null,
) {
    val insets = WindowInsets.systemBars.asPaddingValues()
    val padding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = insets.calculateTopPadding() + 56.dp,
        bottom = insets.calculateBottomPadding() + 24.dp,
    )
    val spacing = Arrangement.spacedBy(12.dp)

    // Cada bloque es una función que agrega ítems a una lista; así se reparten entre uno o dos paneles.
    val summary: LazyListScope.() -> Unit = {
        if (banner != null) item { banner() }
        if (error != null) item { ErrorBanner(error) }
        if (snapshot == null) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Buscando el clima…", "Getting the weather…"), style = MaterialTheme.typography.titleMedium, color = OnSky)
                }
            }
        } else {
            item { Box(Modifier.reveal(0, shownAt)) { Hero(snapshot, isCurrentLocation) } }
            items(snapshot.alerts, key = { "alerta-" + it.id }) { alert -> Box(Modifier.reveal(1, shownAt)) { AlertCard(alert) } }
            // Las alertas ya tienen su tarjeta: la frase busca lo siguiente más útil.
            Insights.headline(snapshot.copy(alerts = emptyList()))?.let { text ->
                item { Box(Modifier.reveal(1, shownAt)) { InsightChip(text) } }
            }
        }
    }
    val details: LazyListScope.(Boolean) -> Unit = { twoPane ->
        if (snapshot != null) {
            item {
                Box(Modifier.reveal(2, shownAt)) {
                    GlassCard(tr("Próximas horas", "Next hours")) {
                        HourlyChart(snapshot.upcomingHours(24))
                    }
                }
            }
            item { Box(Modifier.reveal(3, shownAt)) { DailyCard(snapshot, onDay = onDay) } }
            if (!twoPane) {
                item { Box(Modifier.reveal(4, shownAt)) { RadarCard(onClick = { onOpenRadar(snapshot.latitude, snapshot.longitude) }) } }
            }
            item { Box(Modifier.reveal(5, shownAt)) { DetailsGrid(snapshot) } }
            item {
                Text(
                    tr("Actualizado a las", "Updated at") + " ${updatedAt(snapshot.fetchedAt)} · " + tr("Datos de Open-Meteo", "Data by Open-Meteo"),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSkyVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TwoPaneWidth) {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.weight(0.42f).fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = spacing,
                ) {
                    summary()
                    if (snapshot != null) {
                        item { Box(Modifier.reveal(4, shownAt)) { RadarCard(onClick = { onOpenRadar(snapshot.latitude, snapshot.longitude) }) } }
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(0.58f).fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = spacing,
                ) {
                    details(true)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = spacing,
            ) {
                summary()
                details(false)
            }
        }
    }
}

/** Aviso de versión nueva, arriba del pronóstico. */
@Composable
private fun UpdateBanner(release: AppRelease, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp)) {
            Text(
                tr("Climatin ${release.versionName} está disponible", "Climatin ${release.versionName} is available"),
                style = MaterialTheme.typography.titleMedium,
            )
            release.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(tr("Ahora no", "Not now")) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = onUpdate) { Text(tr("Actualizar", "Update")) }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Temperatura grande directo sobre el cielo, como la app de clima del Pixel. */
@Composable
private fun Hero(snapshot: WeatherSnapshot, isCurrentLocation: Boolean) {
    val current = snapshot.current
    val today = snapshot.today()
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCurrentLocation) {
                Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = tr("Tu ubicación", "Your location"),
                    tint = OnSky,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                snapshot.place ?: tr("Tu ubicación", "Your location"),
                style = MaterialTheme.typography.headlineSmall,
                color = OnSky,
            )
        }
        Text(
            longDate(snapshot.nowAtPlace().toLocalDate()),
            style = MaterialTheme.typography.bodyLarge,
            color = OnSkyVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val shownTemperature by animateFloatAsState(
                    current.temperature.toFloat(),
                    tween(900, easing = EaseOutCubic),
                    label = "temperatura",
                )
                // El número se achica si no entra en una línea (panel angosto o letra grande),
                // así el "°" nunca baja de renglón.
                val temperatureText = shownTemperature.toDouble().degrees()
                val measurer = rememberTextMeasurer()
                val measured = measurer.measure(
                    temperatureText,
                    TextStyle(fontSize = 112.sp, fontWeight = FontWeight.Light),
                    softWrap = false,
                ).size.width
                val fit = (constraints.maxWidth.toFloat() / measured.coerceAtLeast(1)).coerceAtMost(1f) * 0.97f
                val temperatureSize = 112.sp * fit.coerceAtLeast(0.4f)
            Column {
                Text(
                    temperatureText,
                    fontSize = temperatureSize,
                    lineHeight = temperatureSize,
                    fontWeight = FontWeight.Light,
                    color = OnSky,
                    maxLines = 1,
                    softWrap = false,
                )
                AnimatedContent(
                    targetState = WeatherCodes.description(current.code),
                    transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(200))) },
                    label = "estado",
                ) { description ->
                    Text(description, style = MaterialTheme.typography.titleLarge, color = OnSky)
                }
                if (today != null) {
                    Text(
                        tr("Máx", "High") + " ${today.max.degrees()} · " + tr("Mín", "Low") + " ${today.min.degrees()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSkyVariant,
                    )
                }
                Text(
                    tr("Sensación térmica", "Feels like") + " ${current.feelsLike.degrees()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSkyVariant,
                )
            }
            }
            AnimatedWeatherIcon(current.code, current.isDay, size = 120.dp)
        }
    }
}

/** Alerta oficial: tocarla muestra u oculta la descripción completa. */
@Composable
private fun AlertCard(alert: WeatherAlert) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(false) }
    val severe = alert.isSevere
    Surface(
        color = if (severe) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (severe) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .pressable { expanded = !expanded }
            .animateContentSize(Motion.gentle()),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Alerta", "Alert") + ": ${alert.event}", style = MaterialTheme.typography.titleMedium)
            }
            alert.headline?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            alert.description?.takeIf { expanded }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (expanded) tr("Fuente: Servicio Meteorológico Nacional", "Source: Argentina's National Weather Service") else tr("Tocá para ver el detalle", "Tap for details"),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RadarCard(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .pressable(onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(WeatherCodes.icon(61)),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Mapa de lluvia", "Rain map"), style = MaterialTheme.typography.titleMedium)
                Text(
                    tr("Dónde llueve ahora y en las próximas horas", "Where it rains now and in the next hours"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InsightChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = CircleShape,
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Tarjeta semitransparente sobre el cielo animado. */
@Composable
fun GlassCard(title: String?, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

@Composable
private fun DailyCard(snapshot: WeatherSnapshot, onDay: (Day) -> Unit) {
    val days = snapshot.upcomingDays(14)
    if (days.isEmpty()) return
    val weekMin = days.minOf { it.min }
    val weekMax = days.maxOf { it.max }
    val today = snapshot.nowAtPlace().toLocalDate()
    GlassCard(tr("Próximos ${days.size} días", "Next ${days.size} days")) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            days.forEach { day ->
                DayRow(
                    day,
                    dayLabel(day.localDate, today),
                    weekMin,
                    weekMax,
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .pressable { onDay(day) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DayRow(day: Day, label: String, weekMin: Double, weekMax: Double, modifier: Modifier = Modifier) {
    val probability = day.precipitationProbability ?: 0
    val spoken = buildString {
        append("$label: ${WeatherCodes.description(day.code)}, ")
        append(tr("máxima", "high") + " ${day.max.degrees()}, " + tr("mínima", "low") + " ${day.min.degrees()}")
        if (probability >= 10) append(", " + tr("lluvia", "rain") + " $probability%")
    }
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(72.dp))
        Image(
            painter = painterResource(WeatherCodes.icon(day.code)),
            contentDescription = WeatherCodes.description(day.code),
            modifier = Modifier.size(28.dp),
        )
        Text(
            if (probability >= 10) "$probability%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(40.dp),
        )
        Text(
            day.min.degrees(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        TemperatureRange(
            min = day.min,
            max = day.max,
            weekMin = weekMin,
            weekMax = weekMax,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            day.max.degrees(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(36.dp),
        )
    }
}

/** Barra con el rango del día dentro del rango de la semana, como en el clima del Pixel. */
@Composable
private fun TemperatureRange(min: Double, max: Double, weekMin: Double, weekMax: Double, modifier: Modifier) {
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    val start = ((min - weekMin) / span).toFloat().coerceIn(0f, 1f)
    val end = ((max - weekMin) / span).toFloat().coerceIn(start, 1f)
    val colors = MaterialTheme.colorScheme
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    val grow by animateFloatAsState(if (grown) 1f else 0f, tween(700, delayMillis = 250, easing = EaseOutCubic), label = "rango")
    Row(
        modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh),
    ) {
        if (start > 0f) Spacer(Modifier.weight(start))
        Box(
            Modifier
                .weight((end - start).coerceAtLeast(0.04f))
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = grow
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(colors.primary, colors.tertiary))),
        )
        if (end < 1f) Spacer(Modifier.weight(1f - end))
    }
}

private data class Detail(val label: String, val value: String, val note: String? = null)

@Composable
private fun DetailsGrid(snapshot: WeatherSnapshot) {
    val current = snapshot.current
    val today = snapshot.today()
    val details = buildList {
        add(Detail(tr("Humedad", "Humidity"), "${current.humidity}%"))
        add(Detail(tr("Viento", "Wind"), windText(current.windSpeed)))
        (current.uvIndex ?: today?.uvIndex)?.let {
            add(Detail(tr("Índice UV", "UV index"), "${it.roundToInt()}", WeatherCodes.uvLabel(it).replaceFirstChar { c -> c.uppercase() }))
        }
        snapshot.airQuality?.europeanAqi?.let {
            add(Detail(tr("Calidad del aire", "Air quality"), WeatherCodes.airQualityLabel(it), tr("Índice", "Index") + " $it"))
        }
        current.pressure?.let { add(Detail(tr("Presión", "Pressure"), pressureText(it))) }
        current.visibility?.let {
            val km = it / 1000
            add(Detail(tr("Visibilidad", "Visibility"), if (km >= 10) "+10 km" else "${km.roundToInt()} km"))
        }
        add(Detail(tr("Lluvia hoy", "Rain today"), "${today?.precipitationProbability ?: 0}%"))
        add(Detail(tr("Luna", "Moon"), Moon.phaseName(snapshot.nowAtPlace().toLocalDate())))
        today?.let {
            add(Detail(tr("Amanecer", "Sunrise"), timeOf(it.sunrise)))
            add(Detail(tr("Atardecer", "Sunset"), timeOf(it.sunset)))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        details.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { detail -> DetailTile(detail, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailTile(detail: Detail, modifier: Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                detail.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(detail.value, style = MaterialTheme.typography.titleLarge)
            if (detail.note != null) {
                Text(
                    detail.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
