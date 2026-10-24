package com.mnavarrosqa.clima.ui

import android.Manifest
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
                "El clima donde estés",
                style = MaterialTheme.typography.headlineMedium,
                color = OnSky,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Necesito tu ubicación aproximada para mostrarte el pronóstico y mantener los widgets al día.",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSkyVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onAllow) { Text("Permitir ubicación") }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onPickCity) { Text("Elegir una ciudad") }
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
                modifier = Modifier.weight(1f),
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
                Icon(Icons.Rounded.Menu, contentDescription = "Ciudades y ajustes", tint = OnSky)
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
) {
    val snapshot by remember(placeId) { viewModel.snapshots(placeId) }
        .collectAsStateWithLifecycle(viewModel.cached(placeId))
    val insets = WindowInsets.systemBars.asPaddingValues()
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = insets.calculateTopPadding() + 56.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null) item { ErrorBanner(error) }
        if (current == null) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Buscando el clima…", style = MaterialTheme.typography.titleMedium, color = OnSky)
                }
            }
        } else {
            item { Box(Modifier.reveal(0, shownAt)) { Hero(current, isCurrentLocation = placeId == CURRENT_PLACE_ID) } }
            items(current.alerts, key = { "alerta-" + it.id }) { alert -> Box(Modifier.reveal(1, shownAt)) { AlertCard(alert) } }
            // Las alertas ya tienen su tarjeta: la frase busca lo siguiente más útil.
            Insights.headline(current.copy(alerts = emptyList()))?.let { text ->
                item { Box(Modifier.reveal(1, shownAt)) { InsightChip(text) } }
            }
            item {
                Box(Modifier.reveal(2, shownAt)) {
                    GlassCard("Próximas horas") {
                        HourlyChart(current.upcomingHours(24))
                    }
                }
            }
            item { Box(Modifier.reveal(3, shownAt)) { DailyCard(current, onDay = { selectedDay = it }) } }
            item { Box(Modifier.reveal(4, shownAt)) { RadarCard(onClick = { onOpenRadar(current.latitude, current.longitude) }) } }
            item { Box(Modifier.reveal(5, shownAt)) { DetailsGrid(current) } }
            item {
                Text(
                    "Actualizado a las ${updatedAt(current.fetchedAt)} · Datos de Open-Meteo",
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
                    contentDescription = "Tu ubicación",
                    tint = OnSky,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                snapshot.place ?: "Tu ubicación",
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
            Column(Modifier.weight(1f)) {
                val shownTemperature by animateFloatAsState(
                    current.temperature.toFloat(),
                    tween(900, easing = EaseOutCubic),
                    label = "temperatura",
                )
                Text(
                    shownTemperature.toDouble().degrees(),
                    fontSize = 112.sp,
                    lineHeight = 112.sp,
                    fontWeight = FontWeight.Light,
                    color = OnSky,
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
                        "Máx ${today.max.degrees()} · Mín ${today.min.degrees()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSkyVariant,
                    )
                }
                Text(
                    "Sensación térmica ${current.feelsLike.degrees()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSkyVariant,
                )
            }
            AnimatedWeatherIcon(current.code, current.isDay, size = 140.dp)
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
                Text("Alerta: ${alert.event}", style = MaterialTheme.typography.titleMedium)
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
                if (expanded) "Fuente: Servicio Meteorológico Nacional" else "Tocá para ver el detalle",
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
                Text("Radar de lluvia", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Mirá cómo se mueven las nubes de lluvia",
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
    GlassCard("Próximos ${days.size} días") {
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(72.dp))
        Image(
            painter = painterResource(WeatherCodes.icon(day.code)),
            contentDescription = WeatherCodes.description(day.code),
            modifier = Modifier.size(28.dp),
        )
        val probability = day.precipitationProbability ?: 0
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
        add(Detail("Humedad", "${current.humidity}%"))
        add(Detail("Viento", windText(current.windSpeed)))
        (current.uvIndex ?: today?.uvIndex)?.let {
            add(Detail("Índice UV", "${it.roundToInt()}", WeatherCodes.uvLabel(it).replaceFirstChar { c -> c.uppercase() }))
        }
        snapshot.airQuality?.europeanAqi?.let {
            add(Detail("Calidad del aire", WeatherCodes.airQualityLabel(it), "Índice $it"))
        }
        current.pressure?.let { add(Detail("Presión", pressureText(it))) }
        current.visibility?.let {
            val km = it / 1000
            add(Detail("Visibilidad", if (km >= 10) "+10 km" else "${km.roundToInt()} km"))
        }
        add(Detail("Lluvia hoy", "${today?.precipitationProbability ?: 0}%"))
        add(Detail("Luna", Moon.phaseName(snapshot.nowAtPlace().toLocalDate())))
        today?.let {
            add(Detail("Amanecer", timeOf(it.sunrise)))
            add(Detail("Atardecer", timeOf(it.sunset)))
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
