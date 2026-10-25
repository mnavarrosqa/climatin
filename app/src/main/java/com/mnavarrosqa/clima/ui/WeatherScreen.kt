package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme
import com.mnavarrosqa.clima.ui.theme.Appearance
import com.mnavarrosqa.clima.ui.theme.HeroLine
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.windText
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import com.mnavarrosqa.clima.core.R as CoreR
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
import androidx.activity.compose.PredictiveBackHandler
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import com.mnavarrosqa.clima.ui.theme.Flex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.StateFlow
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnavarrosqa.clima.data.AppRelease
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Day
import com.mnavarrosqa.clima.data.LocationProvider
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.WeatherAlert
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.longDate
import com.mnavarrosqa.clima.data.updatedAt
import com.mnavarrosqa.clima.data.ageText
import kotlin.math.roundToInt

internal val OnSky = Color.White
internal val OnSkyVariant = Color.White.copy(alpha = 0.9f)

/** Sombra suave para que el texto blanco se lea sobre un cielo claro, como en el Clima de Apple. */
private val SkyShadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 16f)

private fun TextStyle.onSky() = copy(shadow = SkyShadow)

/** Destino de un acceso directo desde un widget: una ciudad y, opcionalmente, un día (yyyy-MM-dd) o el mapa de lluvia. */
data class DeepLinkTarget(val placeId: String, val day: String?, val radar: Boolean = false, val alert: String? = null)

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
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Bienvenida: la primera vez (sin permiso de ubicación ni ciudades) o desde Ajustes → Acerca de.
    var showWelcome by rememberSaveable {
        mutableStateOf(!Welcome.seen(context) && !LocationProvider.hasPermission(context) && viewModel.places.value.isEmpty())
    }
    // Ciudad tocada en la lista: el pronóstico se abre en esa página.
    var cityTarget by remember { mutableStateOf<DeepLinkTarget?>(null) }
    // "lat,lon" del radar abierto, o null.
    var radarAt by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.refresh()
    }

    LaunchedEffect(hasPermission, places.size) { viewModel.refreshIfStale() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.resumePendingInstall()
        viewModel.recheckUpdates()
        // Si la app quedó abierta en segundo plano, el sistema pudo demorar las actualizaciones:
        // al volver se baja lo nuevo si tiene más de 15 minutos.
        viewModel.refreshIfStale()
        // El permiso se puede dar o quitar desde los ajustes del sistema.
        hasPermission = LocationProvider.hasPermission(context)
    }
    val currentSnapshot by remember { viewModel.snapshots(CURRENT_PLACE_ID) }
        .collectAsStateWithLifecycle(viewModel.cached(CURRENT_PLACE_ID))
    // Un acceso directo siempre lleva al pronóstico, aunque estés en otra pantalla.
    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            showCities = false
            showSettings = false
            // Un widget puede pedir abrir directo el mapa de lluvia de su ciudad.
            val place = if (deepLink.radar) viewModel.cached(deepLink.placeId) ?: viewModel.cached(CURRENT_PLACE_ID) else null
            radarAt = place?.let { "${it.latitude},${it.longitude}" }
            if (place != null) onDeepLinkHandled()
        }
    }
    // Si la app se cerró por un error, se ofrece compartirlo (queda solo en el teléfono hasta que se comparte).
    var crash by remember { mutableStateOf(com.mnavarrosqa.clima.CrashLog.last(context)) }
    crash?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                com.mnavarrosqa.clima.CrashLog.clear(context)
                crash = null
            },
            title = { Text(tr("Climatin se cerró", "Climatin closed unexpectedly")) },
            text = {
                Text(
                    tr(
                        "Hubo un error la última vez. Si lo compartís, se puede arreglar: tiene la versión, el modelo del teléfono y dónde falló, nada personal.",
                        "Something went wrong last time. Sharing it helps fix it: it has the version, phone model and where it failed, nothing personal.",
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_SUBJECT, "Error de Climatin")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, text),
                                tr("Compartir el error", "Share the error"),
                            ),
                        )
                    }
                    com.mnavarrosqa.clima.CrashLog.clear(context)
                    crash = null
                }) { Text(tr("Compartir", "Share")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    com.mnavarrosqa.clima.CrashLog.clear(context)
                    crash = null
                }) { Text(tr("Descartar", "Dismiss")) }
            },
        )
    }
    // Gesto "atrás" predictivo: mientras se desliza, la pantalla se achica y deja ver que vuelve.
    var backProgress by remember { mutableFloatStateOf(0f) }
    // La bienvenida abierta desde Ajustes vuelve a Ajustes con "atrás"; la primera vez, "atrás" sale de la app.
    val welcomeFromSettings = showWelcome && Welcome.seen(context)
    PredictiveBackHandler(enabled = showCities || showSettings || radarAt != null || welcomeFromSettings) { events ->
        try {
            events.collect { backProgress = it.progress }
            when {
                welcomeFromSettings -> showWelcome = false
                radarAt != null -> radarAt = null
                showSettings -> showSettings = false
                else -> showCities = false
            }
        } finally {
            backProgress = 0f
        }
    }

    val pages = buildList {
        if (hasPermission || currentSnapshot != null) add(CURRENT_PLACE_ID)
        places.forEach { add(it.id) }
    }

    // El pager vive acá, fuera de AnimatedContent: al ir a Ajustes o Ciudades y volver, sigue en
    // la misma ciudad.
    val pagerState = rememberPagerState { pages.size }

    val radar = radarAt?.split(',')?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 }
    val screen = when {
        showWelcome -> "bienvenida"
        radar != null -> "radar"
        showSettings -> "ajustes"
        showCities -> "ciudades"
        pages.isEmpty() -> "permiso"
        else -> "clima"
    }
    // Sobre el cielo los íconos del sistema van siempre claros; en Ciudades, Ajustes y el mapa,
    // según el tema (lo que pone ClimaTheme).
    val rootView = LocalView.current
    val darkTheme = LocalDarkTheme.current
    val overSky = screen == "clima" || screen == "permiso" || screen == "bienvenida"
    SideEffect {
        (rootView.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, rootView).apply {
                isAppearanceLightStatusBars = !overSky && !darkTheme
                isAppearanceLightNavigationBars = !overSky && !darkTheme
            }
        }
    }
    AnimatedContent(
        targetState = screen,
        modifier = Modifier.graphicsLayer {
            val scale = 1f - 0.1f * backProgress
            scaleX = scale
            scaleY = scale
            shape = RoundedCornerShape((32 * backProgress).dp)
            clip = backProgress > 0f
        },
        transitionSpec = {
            (fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(380, easing = EaseOutCubic), initialScale = 0.94f))
                .togetherWith(fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 1.03f))
        },
        label = "pantalla",
    ) { target ->
        when (target) {
            "bienvenida" -> WelcomeScreen(
                viewModel = viewModel,
                onAllowLocation = { LocationAsk.request(context, launcher) },
                onFinish = {
                    Welcome.markSeen(context)
                    showWelcome = false
                },
            )
            "radar" -> radar?.let { RadarScreen(it[0], it[1], onBack = { radarAt = null }) }
            "ciudades" -> CitiesScreen(
                viewModel = viewModel,
                onBack = { showCities = false },
                onOpenPlace = { id ->
                    cityTarget = DeepLinkTarget(id, null)
                    showCities = false
                },
                onLocationPermission = { granted ->
                    hasPermission = granted
                    if (granted) viewModel.refresh()
                },
            )
            "ajustes" -> SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettings = false },
                onShowWelcome = { showWelcome = true },
                onLocationPermission = { granted ->
                    hasPermission = granted
                    if (granted) viewModel.refresh()
                },
            )
            "permiso" -> PermissionScreen(
                onAllow = { LocationAsk.request(context, launcher) },
                onPickCity = { showCities = true },
            )
            // Al cambiar las unidades se rearma el pronóstico para que todos los textos se reformateen.
            else -> key(units) {
                WeatherPager(
                    pages,
                    pagerState,
                    viewModel,
                    onOpenCities = { showCities = true },
                    onOpenSettings = { showSettings = true },
                    onOpenRadar = { lat, lon -> radarAt = "$lat,$lon" },
                    deepLink = cityTarget ?: deepLink,
                    onDeepLinkHandled = {
                        cityTarget = null
                        onDeepLinkHandled()
                    },
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
    pagerState: PagerState,
    viewModel: WeatherViewModel,
    onOpenCities: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRadar: (Double, Double) -> Unit,
    deepLink: DeepLinkTarget?,
    onDeepLinkHandled: () -> Unit,
) {
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val update by viewModel.updatePrompt.collectAsStateWithLifecycle()
    // Día a abrir en la página de destino del acceso directo.
    var pendingDay by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Alerta a mostrar abierta (desde la notificación del SMN).
    var openAlert by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        val index = pages.indexOf(target.placeId).takeIf { it >= 0 } ?: 0
        // Salto directo (sin recorrer las páginas del medio), y el acceso se da por usado aunque
        // un toque interrumpa: si no, volvería a saltar la próxima vez.
        try {
            pendingDay = target.day?.let { pages[index] to it }
            openAlert = target.alert
            pagerState.scrollToPage(index)
        } finally {
            onDeepLinkHandled()
        }
    }
    // Un toque leve al cambiar de ciudad y al soltar para actualizar.
    val view = LocalView.current
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { Haptics.tick(view) }
    }
    // Páginas cuyo encabezado grande ya quedó arriba, fuera de la vista.
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    val skyId = pages.getOrElse(pagerState.currentPage) { CURRENT_PLACE_ID }
    val skySnapshot by remember(skyId) { viewModel.snapshots(skyId) }
        .collectAsStateWithLifecycle(viewModel.cached(skyId))

    Box(Modifier.fillMaxSize()) {
        AnimatedSky(
            kind = skySnapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY,
            isDay = skySnapshot?.current?.isDay ?: true,
            modifier = Modifier.fillMaxSize(),
            southern = (skySnapshot?.latitude ?: 0.0) < 0,
        )
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                Haptics.raindrops(view)
                viewModel.refresh()
            },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                )
            },
        ) {
            // Con key, el estado de cada página (desplazamiento, encabezado) sigue a su ciudad al reordenar.
            HorizontalPager(state = pagerState, key = { pages[it] }, modifier = Modifier.fillMaxSize()) { page ->
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
                        onCollapsedChange = { collapsed[pages[page]] = it },
                        openAlert = openAlert,
                    )
                }
            }
        }
        // Aviso de versión nueva flotando abajo, como una notificación dentro de la app: se ve
        // aunque hayas bajado en el pronóstico. "Ahora no" lo esconde hasta la próxima versión.
        // Se recuerda la última para que la salida animada tenga qué mostrar.
        var shownRelease by remember { mutableStateOf(update) }
        if (update != null) shownRelease = update
        AnimatedVisibility(
            visible = update != null,
            enter = slideInVertically(Motion.gentle()) { it } + fadeIn(),
            exit = slideOutVertically(Motion.gentle()) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .widthIn(max = 560.dp)
                .zIndex(2f),
        ) {
            shownRelease?.let { release ->
                // El avance se lee adentro del aviso: así cada pedacito bajado no recompone el pronóstico.
                UpdateBanner(
                    release,
                    progressFlow = viewModel.updateProgress,
                    onUpdate = { viewModel.downloadUpdate(release) },
                    onDismiss = viewModel::dismissUpdate,
                )
            }
        }
        val isCollapsed = collapsed[skyId] == true
        val barAlpha by animateFloatAsState(if (isCollapsed) 1f else 0f, Motion.gentle(), label = "barra")
        val barColor = skyColors(
            skySnapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY,
            skySnapshot?.current?.isDay ?: true,
        )[0]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Al bajar, la barra toma el color de arriba del cielo para que el texto se lea.
                .background(barColor.copy(alpha = 0.92f * barAlpha))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenCities) {
                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = tr("Ciudades", "Cities"), tint = OnSky)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CompactHeader(
                    skySnapshot,
                    Modifier
                        .graphicsLayer {
                            alpha = barAlpha
                            translationY = (1f - barAlpha) * 12.dp.toPx()
                        }
                        // Escondida, TalkBack no la lee: el encabezado grande ya dice lo mismo.
                        .then(if (isCollapsed) Modifier else Modifier.clearAndSetSemantics { }),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = 1f - barAlpha }
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
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = tr("Ajustes", "Settings"), tint = OnSky)
            }
        }
    }
}

/** Ciudad, temperatura e ícono en una línea: lo que queda arriba cuando el encabezado grande se va. */
@Composable
private fun CompactHeader(snapshot: WeatherSnapshot?, modifier: Modifier = Modifier) {
    if (snapshot == null) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            snapshot.place ?: tr("Mi ubicación", "My location"),
            style = MaterialTheme.typography.titleMedium.onSky(),
            color = OnSky,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        Text(snapshot.current.temperature.degrees(), style = MaterialTheme.typography.titleLarge.onSky(), color = OnSky)
        Spacer(Modifier.width(4.dp))
        Image(
            painter = painterResource(WeatherCodes.icon(snapshot.current.code, snapshot.current.isDay)),
            contentDescription = WeatherCodes.description(snapshot.current.code),
            modifier = Modifier.size(28.dp),
        )
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
    onCollapsedChange: (Boolean) -> Unit = {},
    openAlert: String? = null,
    banner: (@Composable () -> Unit)? = null,
) {
    val cards = viewModel.cards.collectAsStateWithLifecycle().value.visible
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
        onCollapsedChange = onCollapsedChange,
        cards = cards,
        openAlert = openAlert,
        onRetry = viewModel::refresh,
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
    onCollapsedChange: (Boolean) -> Unit = {},
    cards: List<ForecastCard> = ForecastCard.entries,
    openAlert: String? = null,
    onRetry: () -> Unit = {},
) {
    // La lista del resumen (la única en pantallas angostas) decide si el encabezado se achica.
    val summaryState = rememberLazyListState()
    // Un reloj por minuto: "Actualizado hace…", el aviso de datos viejos y la lluvia de la
    // próxima hora siguen al día aunque la pantalla quede abierta.
    val minute by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    // Datos de hace más de 2 horas: se avisa arriba, no solo en la letra chica del final.
    val stale = snapshot != null && minute - snapshot.fetchedAt > 2 * 60 * 60_000L
    // Se calculan una vez por pronóstico (y por minuto), no en cada recomposición.
    val alerts = remember(snapshot, minute) { snapshot?.alerts.orEmpty().distinctBy { it.id }.filterNot { it.isExpired() } }
    val nextRain = remember(snapshot, minute) { snapshot?.let(::nextHourRain) }
    val headline = remember(snapshot, minute) { snapshot?.let { Insights.headline(it.copy(alerts = emptyList())) } }
    val ageLabel = remember(snapshot, minute) { snapshot?.let { ageText(it.fetchedAt) } }
    val heroIndex = listOfNotNull(banner, error ?: if (stale) "" else null).size
    val collapseAt = with(LocalDensity.current) { 180.dp.roundToPx() }
    val isCollapsed by remember(heroIndex) {
        derivedStateOf {
            summaryState.firstVisibleItemIndex > heroIndex ||
                (summaryState.firstVisibleItemIndex == heroIndex && summaryState.firstVisibleItemScrollOffset > collapseAt)
        }
    }
    LaunchedEffect(isCollapsed) { onCollapsedChange(isCollapsed) }
    val insets = WindowInsets.systemBars.asPaddingValues()
    val padding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = insets.calculateTopPadding() + 56.dp,
        bottom = insets.calculateBottomPadding() + 24.dp,
    )
    val spacing = Arrangement.spacedBy(if (Appearance.compact) 8.dp else 12.dp)

    // Cada bloque es una función que agrega ítems a una lista; así se reparten entre uno o dos paneles.
    val summary: LazyListScope.() -> Unit = {
        if (banner != null) item { banner() }
        if (error != null || stale) {
            item {
                ErrorBanner(
                    message = error,
                    age = ageLabel?.let { tr("Mostrando datos de ", "Showing data from ") + it },
                    onRetry = onRetry,
                )
            }
        }
        if (snapshot == null) {
            // Con error y nada guardado no tiene sentido el esqueleto animado para siempre.
            if (error == null) item { ForecastSkeleton() }
        } else {
            item {
                Box(
                    Modifier
                        .reveal(0, shownAt)
                        .graphicsLayer {
                            // El encabezado se aleja al bajar: se achica, sube más lento y se apaga.
                            val scrolled = if (summaryState.firstVisibleItemIndex == heroIndex) {
                                summaryState.firstVisibleItemScrollOffset.toFloat()
                            } else if (summaryState.firstVisibleItemIndex > heroIndex) {
                                size.height
                            } else {
                                0f
                            }
                            val fraction = (scrolled / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                            alpha = 1f - fraction
                            val scale = 1f - 0.12f * fraction
                            scaleX = scale
                            scaleY = scale
                            translationY = scrolled * 0.4f
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) { Hero(snapshot, isCurrentLocation) }
            }
            items(alerts, key = { "alerta-" + it.id }) { alert -> Box(Modifier.reveal(1, shownAt)) { AlertCard(alert, startExpanded = alert.id == openAlert) } }
            nextRain?.let { values ->
                item(key = "proxima-hora") { Box(Modifier.reveal(1, shownAt)) { NextHourRainCard(snapshot, values) } }
            }
            // Las alertas ya tienen su tarjeta: la frase busca lo siguiente más útil.
            headline?.let { text ->
                item { Box(Modifier.reveal(1, shownAt)) { InsightChip(text) } }
            }
        }
    }
    // Cada tarjeta, en el orden que eligió la persona (Ajustes → Pantalla del clima).
    val card: LazyListScope.(ForecastCard, Int) -> Unit = { which, index ->
        if (snapshot != null) {
            item(key = which.name) {
                Box(Modifier.reveal(index, shownAt)) {
                    when (which) {
                        ForecastCard.HOURS -> GlassCard(tr("Próximas horas", "Next hours")) {
                            val sunEvents = remember(snapshot) { sunEventsOf(snapshot.days).filter { it.time.isAfter(snapshot.nowAtPlace()) } }
                            val hours = remember(snapshot) { snapshot.upcomingHours(24) }
                            val yesterday = remember(snapshot) { snapshot.yesterdayAt(hours) }
                            val alertSpans = remember(snapshot) { alertSpans(snapshot) }
                            HourlyChart(hours, sunEvents = sunEvents, yesterday = yesterday, alerts = alertSpans)
                        }
                        ForecastCard.DAYS -> DailyCard(snapshot, onDay = onDay)
                        ForecastCard.RADAR -> RadarCard(onClick = { onOpenRadar(snapshot.latitude, snapshot.longitude) })
                        ForecastCard.DETAILS -> DetailsGrid(snapshot)
                    }
                }
            }
        }
    }
    val details: LazyListScope.(Boolean) -> Unit = { twoPane ->
        if (snapshot != null) {
            // En dos paneles, el mapa y los detalles van a la izquierda.
            cards.filter { !twoPane || !it.leftPane }.forEachIndexed { i, which -> card(which, 2 + i) }
            item {
                Text(
                    tr("Actualizado", "Updated") + " $ageLabel (${updatedAt(snapshot.fetchedAt)}) · " + tr("Datos de Open-Meteo", "Data by Open-Meteo"),
                    style = MaterialTheme.typography.labelMedium.onSky(),
                    color = OnSkyVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Velo oscuro arriba: el cielo de día es muy claro y la temperatura blanca se perdía.
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.3f),
                    0.5f to Color.Transparent,
                ),
            ),
    ) {
        if (maxWidth >= TwoPaneWidth) {
            Row(Modifier.fillMaxSize()) {
                // Izquierda: el ahora (temperatura, avisos, mapa y detalles); derecha: lo que viene.
                LazyColumn(
                    state = summaryState,
                    modifier = Modifier.weight(0.42f).fillMaxSize(),
                    contentPadding = padding,
                    verticalArrangement = spacing,
                ) {
                    summary()
                    cards.filter { it.leftPane }.forEachIndexed { i, which -> card(which, 2 + i) }
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
                state = summaryState,
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
private fun UpdateBanner(release: AppRelease, progressFlow: StateFlow<Float?>, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    // Como una notificación: ícono de la app, quién avisa y qué hay de nuevo.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape)) {
                    Image(painterResource(CoreR.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
                    Image(
                        painterResource(CoreR.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.45f
                                scaleY = 1.45f
                            },
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Versión nueva", "New version"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        tr("Climatin ${release.versionName} está disponible", "Climatin ${release.versionName} is available"),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            release.notes?.lineSequence()?.firstOrNull { it.isNotBlank() }?.let {
                Text(
                    it.removePrefix("• "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 52.dp, top = 4.dp),
                )
            }
            val downloading = progress
            if (downloading != null) {
                // Mientras baja: barra de avance en vez de botones.
                Spacer(Modifier.height(10.dp))
                WavyProgress(
                    progress = { progressFlow.value ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 52.dp, end = 8.dp),
                )
                Text(
                    tr("Bajando… ${(downloading * 100).roundToInt()}%", "Downloading… ${(downloading * 100).roundToInt()}%"),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 52.dp, top = 6.dp, bottom = 6.dp),
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(tr("Ahora no", "Not now")) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = onUpdate) { Text(tr("Actualizar", "Update")) }
                }
            }
        }
    }
}

/**
 * No se pudo actualizar, o lo que se ve es viejo: qué pasó, de cuándo son los datos que se ven y
 * un botón para reintentar. TalkBack lo anuncia al aparecer.
 */
@Composable
private fun ErrorBanner(message: String?, age: String?, onRetry: () -> Unit) {
    val isError = message != null
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Rounded.Warning else Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                age?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
            TextButton(onClick = onRetry) { Text(tr("Reintentar", "Retry")) }
        }
    }
}

/** Temperatura grande directo sobre el cielo, como la app de clima del Pixel. */
@Composable
private fun Hero(snapshot: WeatherSnapshot, isCurrentLocation: Boolean) {
    val current = snapshot.current
    val today = snapshot.today()
    val compact = Appearance.compact
    // Ajustes → Pantalla del clima: tamaño del número y qué datos van debajo.
    val scale = Appearance.heroSize.scale * if (compact) 0.85f else 1f
    val lines = Appearance.heroLines
    Column(Modifier.padding(horizontal = 8.dp, vertical = if (compact) 0.dp else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCurrentLocation) {
                Icon(
                    Icons.Rounded.LocationOn,
                    contentDescription = tr("Mi ubicación", "My location"),
                    tint = OnSky,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                snapshot.place ?: tr("Mi ubicación", "My location"),
                style = MaterialTheme.typography.headlineSmall.onSky(),
                color = OnSky,
            )
        }
        Text(
            longDate(snapshot.nowAtPlace().toLocalDate()),
            style = MaterialTheme.typography.bodyLarge.onSky(),
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
                // Se mide la temperatura final una vez, no cada número de los 900 ms de animación.
                val targetText = current.temperature.degrees()
                val measured = remember(targetText, scale, measurer) {
                    measurer.measure(
                        targetText,
                        TextStyle(fontSize = 112.sp * scale, fontWeight = FontWeight.Light, fontFamily = Flex),
                        softWrap = false,
                    ).size.width
                }
                val fit = (constraints.maxWidth.toFloat() / measured.coerceAtLeast(1)).coerceAtMost(1f) * 0.97f
                val temperatureSize = 112.sp * scale * fit.coerceAtLeast(0.4f)
            Column {
                Text(
                    temperatureText,
                    fontSize = temperatureSize,
                    lineHeight = temperatureSize,
                    fontWeight = FontWeight.Light,
                    fontFamily = Flex,
                    color = OnSky,
                    style = TextStyle(shadow = SkyShadow),
                    maxLines = 1,
                    softWrap = false,
                )
                AnimatedContent(
                    targetState = WeatherCodes.description(current.code),
                    transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(200))) },
                    label = "estado",
                ) { description ->
                    Text(description, style = MaterialTheme.typography.titleLarge.onSky(), color = OnSky)
                }
                if (today != null && HeroLine.HIGH_LOW in lines) {
                    Text(
                        tr("Máx", "High") + " ${today.max.degrees()} · " + tr("Mín", "Low") + " ${today.min.degrees()}",
                        style = MaterialTheme.typography.bodyLarge.onSky(),
                        color = OnSkyVariant,
                    )
                }
                heroExtras(snapshot, lines).takeIf { it.isNotEmpty() }?.let { extras ->
                    Text(
                        extras.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium.onSky(),
                        color = OnSkyVariant,
                    )
                }
            }
            }
            AnimatedWeatherIcon(current.code, current.isDay, size = 120.dp * scale.coerceAtMost(1.1f))
        }
    }
}

/** Los datos elegidos para debajo de la temperatura, salvo máxima y mínima (que van en su renglón). */
private fun heroExtras(snapshot: WeatherSnapshot, lines: Set<HeroLine>): List<String> {
    val current = snapshot.current
    val today = snapshot.today()
    return HeroLine.entries.filter { it in lines }.mapNotNull { line ->
        when (line) {
            HeroLine.HIGH_LOW -> null
            HeroLine.FEELS_LIKE -> tr("Sensación térmica", "Feels like") + " ${current.feelsLike.degrees()}"
            HeroLine.WIND -> tr("Viento", "Wind") + " ${windText(current.windSpeed)}"
            HeroLine.HUMIDITY -> tr("Humedad", "Humidity") + " ${current.humidity}%"
            HeroLine.RAIN -> today?.precipitationProbability?.let { tr("Lluvia $it% hoy", "Rain $it% today") }
            HeroLine.SUN -> nextSunText(snapshot)
        }
    }
}

/** "Atardecer 19:34" o, si ya se puso, "Amanecer 6:12" del día siguiente. */
private fun nextSunText(snapshot: WeatherSnapshot): String? {
    val now = snapshot.nowAtPlace()
    val events = snapshot.days.flatMap { day ->
        listOfNotNull(
            runCatching { java.time.LocalDateTime.parse(day.sunrise) }.getOrNull()?.let { true to it },
            runCatching { java.time.LocalDateTime.parse(day.sunset) }.getOrNull()?.let { false to it },
        )
    }
    val (sunrise, time) = events.filter { it.second.isAfter(now) }.minByOrNull { it.second } ?: return null
    return (if (sunrise) tr("Amanecer", "Sunrise") else tr("Atardecer", "Sunset")) + " " + shortHour(time)
}

/** Las alertas vigentes o por venir, en la hora del lugar y con el color de su nivel. */
fun alertSpans(snapshot: WeatherSnapshot): List<AlertSpan> {
    val zone = snapshot.timezone?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() }
        ?: java.time.ZoneOffset.ofTotalSeconds(snapshot.utcOffsetSeconds)
    fun local(text: String?) = text?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull() }
    return snapshot.alerts.filterNot { it.isExpired() }.mapNotNull { alert ->
        val end = local(alert.expires) ?: return@mapNotNull null
        val start = local(alert.onset) ?: snapshot.nowAtPlace()
        val color = when {
            alert.severity.equals("Extreme", true) -> Color(0xFFD32F2F)
            alert.severity.equals("Severe", true) -> Color(0xFFF57C00)
            else -> Color(0xFFF2B705)
        }
        AlertSpan(start, end, color)
    }
}

/** Alerta oficial: tocarla muestra u oculta la descripción completa. */
@Composable
private fun AlertCard(alert: WeatherAlert, startExpanded: Boolean = false) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(startExpanded) }
    // Si llega desde la notificación con la tarjeta ya en pantalla, se abre igual.
    LaunchedEffect(startExpanded) { if (startExpanded) expanded = true }
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
        contentColor = MaterialTheme.colorScheme.onSurface,
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
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val compact = Appearance.compact
        Column(Modifier.padding(vertical = if (compact) 12.dp else 16.dp)) {
            if (title != null) {
                CardLabel(title, Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            }
            content()
        }
    }
}

/** Título de tarjeta en versalitas chicas y espaciadas, como en el Clima de Apple. */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun DailyCard(snapshot: WeatherSnapshot, onDay: (Day) -> Unit) {
    // Compacto: una semana en vez de dos.
    val compact = Appearance.compact
    val days = snapshot.upcomingDays(if (compact) 7 else 14)
    if (days.isEmpty()) return
    val weekMin = days.minOf { it.min }
    val weekMax = days.maxOf { it.max }
    val today = snapshot.nowAtPlace().toLocalDate()
    GlassCard(tr("Próximos ${days.size} días", "Next ${days.size} days")) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            days.forEach { day ->
                val isToday = day.localDate == today
                DayRow(
                    day,
                    dayLabel(day.localDate, today),
                    weekMin,
                    weekMax,
                    now = snapshot.current.temperature.takeIf { isToday },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        // Hoy se destaca con un fondo suave, como en el clima del Pixel.
                        .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .pressable { onDay(day) }
                        .padding(horizontal = 12.dp, vertical = if (compact) 3.dp else 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DayRow(day: Day, label: String, weekMin: Double, weekMax: Double, now: Double? = null, modifier: Modifier = Modifier) {
    val probability = day.precipitationProbability ?: 0
    val spoken = buildString {
        append("$label: ${WeatherCodes.description(day.code)}, ")
        append(tr("máxima", "high") + " ${day.max.degrees()}, " + tr("mínima", "low") + " ${day.min.degrees()}")
        if (now != null) append(", " + tr("ahora", "now") + " ${now.degrees()}")
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
            now = now,
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

/**
 * Barra con el rango del día dentro del rango de la semana, como en el clima del Pixel.
 * En la fila de hoy, un punto marca la temperatura de ahora.
 */
@Composable
private fun TemperatureRange(min: Double, max: Double, weekMin: Double, weekMax: Double, now: Double?, modifier: Modifier) {
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    val start = ((min - weekMin) / span).toFloat().coerceIn(0f, 1f)
    val end = ((max - weekMin) / span).toFloat().coerceIn(start, 1f)
    val colors = MaterialTheme.colorScheme
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    val grow by animateFloatAsState(if (grown) 1f else 0f, tween(700, delayMillis = 250, easing = EaseOutCubic), label = "rango")
    val dark = LocalDarkTheme.current
    val rangeColors = remember(min, max, dark) { TempColors.between(min, max).map { TempColors.forTheme(it, dark) } }
    val nowAt = now?.let { ((it - weekMin) / span).toFloat().coerceIn(0f, 1f) }
    val dotColor = colors.onSurface
    val ringColor = colors.surfaceContainer
    Row(
        modifier
            .height(12.dp)
            .drawWithContent {
                drawContent()
                if (nowAt != null && grow > 0.99f) {
                    val center = Offset(size.width * nowAt, size.height / 2)
                    drawCircle(ringColor, radius = 6.dp.toPx(), center = center)
                    drawCircle(dotColor, radius = 4.dp.toPx(), center = center)
                }
            }
            .padding(vertical = 3.dp)
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
                // Colores de la escala de temperatura, de la mínima a la máxima del día.
                .background(Brush.horizontalGradient(rangeColors)),
        )
        if (end < 1f) Spacer(Modifier.weight(1f - end))
    }
}
