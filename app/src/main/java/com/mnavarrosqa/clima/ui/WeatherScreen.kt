package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.mnavarrosqa.clima.ui.theme.LocalDarkTheme
import com.mnavarrosqa.clima.ui.theme.Appearance
import com.mnavarrosqa.clima.ui.theme.HeroLine
import com.mnavarrosqa.clima.ui.theme.SkyBackdrop
import com.mnavarrosqa.clima.data.PlacePhoto
import com.mnavarrosqa.clima.data.PlacePhotos
import androidx.compose.animation.Crossfade
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
import androidx.activity.BackEventCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.Share
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import kotlinx.coroutines.launch
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import com.mnavarrosqa.clima.ui.theme.Flex
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import com.mnavarrosqa.clima.data.RainMap
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.ButtonDefaults
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
import com.mnavarrosqa.clima.data.updatedAt
import com.mnavarrosqa.clima.data.ageText
import kotlin.math.roundToInt
import kotlin.math.sign
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

internal val OnSky = Color.White
internal val OnSkyVariant = Color.White.copy(alpha = 0.9f)

/** Sombra suave para que el texto blanco se lea sobre un cielo claro, como en el Clima de Apple. */
private val SkyShadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 2f), 16f)

internal fun TextStyle.onSky() = copy(shadow = SkyShadow)

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
    // Comparar ciudades: las que se eligen de entrada (la que se mira primero), o null si está cerrada.
    var compareIds by rememberSaveable { mutableStateOf<String?>(null) }
    // Bienvenida: la primera vez (sin permiso de ubicación ni ciudades) o desde Ajustes → Acerca de.
    var showWelcome by rememberSaveable {
        mutableStateOf(!Welcome.seen(context) && !LocationProvider.hasPermission(context) && viewModel.places.value.isEmpty())
    }
    // Ciudad tocada en la lista: el pronóstico se abre en esa página.
    var cityTarget by remember { mutableStateOf<DeepLinkTarget?>(null) }
    // Al llegar a Ciudades desde "Elegir una ciudad": el buscador toma el foco.
    var focusCitySearch by remember { mutableStateOf(false) }
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
            compareIds = null
            // Un widget puede pedir abrir directo el mapa de lluvia de su ciudad.
            val own = if (deepLink.radar) viewModel.cached(deepLink.placeId) else null
            val place = own ?: if (deepLink.radar) viewModel.cached(CURRENT_PLACE_ID) else null
            radarAt = place?.let { "${it.latitude},${it.longitude}" }
            if (place != null) {
                // El pronóstico también queda en esa ciudad: al salir del mapa se veía la anterior.
                // El pager no está en pantalla ahora; salta cuando vuelve (igual que al tocar una ciudad).
                cityTarget = DeepLinkTarget(if (own != null) deepLink.placeId else CURRENT_PLACE_ID, null)
                onDeepLinkHandled()
            }
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
    // Gesto "atrás" predictivo: mientras se desliza, la pantalla se achica, se corre hacia el lado
    // del dedo y deja ver que vuelve. Si se arrepiente, vuelve a su lugar con un resorte, sin saltar.
    val backShown = remember { Animatable(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val backScope = rememberCoroutineScope()
    // La bienvenida abierta desde Ajustes vuelve a Ajustes con "atrás"; la primera vez, "atrás" sale de la app.
    val welcomeFromSettings = showWelcome && Welcome.seen(context)
    PredictiveBackHandler(
        enabled = showCities || showSettings || radarAt != null || compareIds != null || welcomeFromSettings,
    ) { events ->
        try {
            events.collect { event ->
                backEdge = event.swipeEdge
                backShown.snapTo(event.progress)
            }
            when {
                welcomeFromSettings -> showWelcome = false
                radarAt != null -> radarAt = null
                compareIds != null -> compareIds = null
                showSettings -> showSettings = false
                else -> showCities = false
            }
            // Gesto completo: la pantalla de destino entra entera. Con el resorte de vuelta se veía
            // achicada y corrida un instante y después saltaba a su lugar.
            backShown.snapTo(0f)
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            // Se arrepintió: vuelve con el resorte. Esta corrutina ya está cancelada, así que la
            // animación va en otra.
            backScope.launch { backShown.animateTo(0f, Motion.gentle()) }
            throw cancel
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
    // El último mapa abierto: mientras su pantalla se va, sigue teniendo qué dibujar.
    var shownRadar by remember { mutableStateOf(radar) }
    if (radar != null) shownRadar = radar
    val screen = when {
        showWelcome -> "bienvenida"
        radar != null -> "radar"
        compareIds != null -> "comparar"
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
    // Barra flotante en las pantallas principales (también el mapa y el pedido de ubicación: sin
    // ella, desde ahí no se llegaba a Ajustes); comparar y la bienvenida ocupan toda la pantalla.
    val showBar = screen != "bienvenida" && screen != "comparar"
    val tabId = pages.getOrElse(pagerState.currentPage) { CURRENT_PLACE_ID }
    val tabSnapshot by remember(tabId) { viewModel.snapshots(tabId) }
        .collectAsStateWithLifecycle(viewModel.cached(tabId))
    // El mapa se abre en la ciudad que se mira; si todavía no tiene pronóstico, en donde estás.
    val mapSnapshot = tabSnapshot ?: currentSnapshot
    // Cada pestaña guarda lo suyo (desplazamiento, página de Ajustes abierta, búsqueda) mientras
    // se mira otra: AnimatedContent solo, las rearmaba de cero en cada cambio.
    val tabStates = rememberSaveableStateHolder()
    // El mapa guarda su estado por lugar: abrirlo en otra ciudad no hereda el zoom ni el centro.
    val mapKey = shownRadar?.let { "mapa:${it[0]},${it[1]}" }
    var lastMapKey by remember { mutableStateOf(mapKey) }
    LaunchedEffect(mapKey) {
        val old = lastMapKey
        if (old != null && old != mapKey) tabStates.removeState(old)
        lastMapKey = mapKey
    }
    // La entrada escalonada de las tarjetas corre una vez por apertura de la app, la primera vez
    // que se ve el pronóstico; no en cada vuelta de otra pestaña. No es estado: se anota y listo.
    val forecastShownAt = remember { LongArray(1) }
    // El pronóstico pide esconder la barra al bajar. La raíz no lee el valor: solo la barra, así
    // el desplazamiento no recompone todo esto.
    val forecastHidesBar = remember { mutableStateOf(false) }
    // Con TalkBack (exploración táctil) la barra no se esconde nunca: no se la encontraría.
    val touchExploring = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        touchExploring.value = manager?.isTouchExplorationEnabled == true
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploring.value = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    // Cada vez que se vuelve a tocar Clima estando en Clima, sube: el pronóstico mira si cambió.
    val scrollToTop = remember { mutableIntStateOf(0) }
    val selectTab: (NavTab) -> Unit = { tab ->
        when (tab) {
            NavTab.CLIMA -> {
                if (screen == "clima") scrollToTop.intValue++
                radarAt = null
                showCities = false
                showSettings = false
            }
            // Ya en el mapa, no se vuelve a abrir (perdería el lugar y el zoom).
            NavTab.MAPA -> if (radarAt == null) mapSnapshot?.let { radarAt = "${it.latitude},${it.longitude}" }
            NavTab.CIUDADES -> {
                radarAt = null
                showSettings = false
                showCities = true
            }
            NavTab.AJUSTES -> {
                radarAt = null
                showCities = false
                showSettings = true
            }
        }
    }
    val openCitySearch = {
        focusCitySearch = true
        // La bienvenida también se abre desde Ajustes: si quedaba abierto, tapaba a Ciudades.
        showSettings = false
        showCities = true
    }
    // La foto de fondo del pronóstico, si hay: con riel se dibuja acá, a pantalla completa, para
    // que siga detrás del riel (si no, la franja del riel mostraba el cielo celeste al lado de la foto).
    var railPhoto by remember { mutableStateOf<RailPhoto?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Pantalla ancha: la barra va de costado y no le saca alto al contenido.
        val rail = maxWidth >= NavRailMinWidth
        val railSpace by animateDpAsState(if (rail && showBar) NavRailSpace else 0.dp, Motion.gentle(), label = "riel")
        val photoBehindRail = railPhoto?.takeIf { rail && showBar && screen == "clima" }
        if (photoBehindRail != null) {
            PhotoBackdrop(photoBehindRail.shown.bitmap, Modifier.fillMaxSize()) {
                if (photoBehindRail.kind in PhotoParticles) {
                    AnimatedSky(photoBehindRail.kind, photoBehindRail.isDay, Modifier.fillMaxSize(), calm = photoBehindRail.calm, particlesOnly = true)
                }
            }
            // El mismo velo que pone el pronóstico sobre su parte, solo en la franja del riel: el
            // pronóstico ya pone el suyo y dos juntos lo oscurecían de más.
            Box(
                Modifier
                    .fillMaxHeight()
                    .background(SkySurface.veil(SkySurface.PHOTO_BRIGHTNESS))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    .width(railSpace),
            )
        } else if (rail && showBar) {
            // Detrás del riel sigue lo que hay al lado: el cielo (con su mismo velo) o el fondo del tema.
            if (overSky) {
                val onForecast = screen == "clima"
                val kind = (if (onForecast) tabSnapshot?.current?.code?.let(WeatherCodes::kind) else null) ?: WeatherKind.PARTLY
                val isDay = (if (onForecast) tabSnapshot?.current?.isDay else null) ?: true
                // Los mismos colores (con amanecer y atardecer) y el mismo velo que la página del
                // pronóstico: si no, la franja del riel se veía de otro tono.
                val sky = SkyTime.colors(kind, isDay, if (onForecast) tabSnapshot else null)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(sky))
                        .background(
                            if (onForecast) {
                                SkySurface.veil(SkySurface.brightness(sky))
                            } else {
                                Brush.verticalGradient(listOf(PermissionScrim, PermissionScrim))
                            },
                        ),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
        CompositionLocalProvider(
            LocalBottomBarSpace provides if (showBar && !rail) BottomBarSpace else 0.dp,
            LocalNavRailSpace provides if (showBar && rail) NavRailSpace else 0.dp,
        ) {
            AnimatedContent(
                targetState = screen,
                modifier = Modifier
                    // El riel va por dentro del recorte y de la barra del sistema apaisada; el
                    // contenido arranca después, así ninguna pantalla tiene que saber del riel.
                    .then(
                        if (rail && showBar) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                        } else {
                            Modifier
                        },
                    )
                    .padding(start = railSpace)
                    .graphicsLayer {
                        // Se lee al dibujar: el gesto no recompone las pantallas.
                        val shownBack = backShown.value
                        val scale = 1f - 0.1f * shownBack
                        scaleX = scale
                        scaleY = scale
                        // Desde el borde izquierdo se corre a la derecha y al revés, como si la empujara el dedo.
                        val direction = if (backEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                        translationX = direction * shownBack * size.width * 0.05f
                        shape = RoundedCornerShape((32 * shownBack).dp)
                        clip = shownBack > 0f
                    },
                transitionSpec = {
                    // Entre pestañas, un fundido corto; lo que se abre encima entra agrandándose.
                    if (initialState in DrillIns || targetState in DrillIns) Motion.drillIn() else Motion.tabSwitch()
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
                            // Si salteó la ubicación y no tiene ciudades, pedirla otra vez enseguida era
                            // insistir: se abre la búsqueda de ciudades.
                            if (!hasPermission && places.isEmpty() && currentSnapshot == null) openCitySearch()
                        },
                        onPickCity = {
                            Welcome.markSeen(context)
                            showWelcome = false
                            openCitySearch()
                        },
                    )
                    "radar" -> shownRadar?.let {
                        tabStates.SaveableStateProvider("mapa:${it[0]},${it[1]}") {
                            RadarScreen(it[0], it[1], onBack = { radarAt = null }, onAddPlace = viewModel::addPlace)
                        }
                    }
                    "comparar" -> CompareScreen(
                        viewModel = viewModel,
                        initialIds = compareIds.orEmpty().split('|').filter { it.isNotEmpty() },
                        onBack = { compareIds = null },
                    )
                    "ciudades" -> tabStates.SaveableStateProvider("ciudades") {
                        CitiesScreen(
                            viewModel = viewModel,
                            onOpenPlace = { id ->
                                cityTarget = DeepLinkTarget(id, null)
                                showCities = false
                            },
                            onLocationPermission = { granted ->
                                hasPermission = granted
                                if (granted) viewModel.refresh()
                            },
                            // Hace falta una segunda ciudad (o "Mi ubicación" y una guardada).
                            canCompare = pages.size > 1,
                            // Arranca con la ciudad que se mira en el pronóstico y las que le siguen.
                            onCompare = {
                                compareIds = (listOf(tabId) + pages.filter { it != tabId }).take(5).joinToString("|")
                            },
                            focusSearch = focusCitySearch,
                            onSearchFocused = { focusCitySearch = false },
                        )
                    }
                    "ajustes" -> tabStates.SaveableStateProvider("ajustes") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { showSettings = false },
                            onShowWelcome = { showWelcome = true },
                            onLocationPermission = { granted ->
                                hasPermission = granted
                                if (granted) viewModel.refresh()
                            },
                        )
                    }
                    "permiso" -> PermissionScreen(
                        onAllow = { LocationAsk.request(context, launcher) },
                        onPickCity = openCitySearch,
                    )
                    else -> tabStates.SaveableStateProvider("clima") {
                        if (forecastShownAt[0] == 0L) forecastShownAt[0] = SystemClock.uptimeMillis()
                        // Al cambiar las unidades se rearma el pronóstico para que todos los textos se reformateen.
                        key(units) {
                            WeatherPager(
                                pages,
                                pagerState,
                                viewModel,
                                onOpenCities = { showCities = true },
                                onOpenRadar = { lat, lon -> radarAt = "$lat,$lon" },
                                shownAt = forecastShownAt[0],
                                onBarHiddenChange = { forecastHidesBar.value = it },
                                scrollToTop = scrollToTop.intValue,
                                photoBehind = rail && showBar,
                                onPhotoChange = { railPhoto = it },
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
        }
        val bar: @Composable (Boolean, Modifier) -> Unit = { vertical, barModifier ->
            FloatingNavBar(
                selected = when (screen) {
                    "ciudades" -> NavTab.CIUDADES
                    "ajustes" -> NavTab.AJUSTES
                    "radar" -> NavTab.MAPA
                    else -> NavTab.CLIMA
                },
                overSky = screen == "clima" || screen == "permiso",
                mapEnabled = mapSnapshot != null || radarAt != null,
                onSelect = selectTab,
                icon = { tab, tint, selected ->
                    when (tab) {
                        // El clima de ahora de la ciudad que se mira, como ícono.
                        NavTab.CLIMA -> Image(
                            painterResource(WeatherCodes.icon(tabSnapshot?.current?.code ?: 2, tabSnapshot?.current?.isDay ?: true)),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        NavTab.MAPA -> Icon(if (selected) MapTabIconFilled else MapTabIcon, contentDescription = null, tint = tint)
                        NavTab.CIUDADES -> Icon(if (selected) CitiesTabIconFilled else CitiesTabIcon, contentDescription = null, tint = tint)
                        NavTab.AJUSTES -> Icon(Icons.Rounded.Settings, contentDescription = null, tint = tint)
                    }
                },
                modifier = barModifier,
                vertical = vertical,
            )
        }
        if (rail) {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInHorizontally(Motion.gentle()) { -it } + fadeIn(),
                exit = slideOutHorizontally(Motion.gentle()) { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                    .padding(start = 12.dp),
            ) { bar(true, Modifier) }
        } else {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(Motion.gentle()) { it } + fadeIn(),
                exit = slideOutVertically(Motion.gentle()) { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                // Al bajar en el pronóstico se va para abajo y vuelve al subir; en las otras
                // pestañas, con TalkBack y en el riel, siempre está. Se lee acá adentro: cambiar
                // de dirección recompone solo la barra, y el resorte solo la redibuja.
                val hideOnScroll = screen == "clima" && forecastHidesBar.value && !touchExploring.value
                val hidden = animateFloatAsState(if (hideOnScroll) 1f else 0f, Motion.gentle(), label = "barra al desplazar")
                val navBottom = WindowInsets.navigationBars.getBottom(LocalDensity.current)
                bar(
                    false,
                    Modifier.graphicsLayer {
                        val gone = hidden.value.coerceIn(0f, 1f)
                        // Sale entera por debajo del borde, con su margen y la barra del sistema.
                        translationY = gone * (size.height + 12.dp.toPx() + navBottom)
                        alpha = 1f - gone
                    },
                )
            }
        }
    }
}

/** Pantallas que se abren encima de las pestañas: entran y salen con escala, no con el fundido corto. */
private val DrillIns = setOf("bienvenida", "comparar")

/** Velo del pedido de ubicación (el mismo de la bienvenida): el cielo de día es muy claro para texto blanco. */
private val PermissionScrim = Color.Black.copy(alpha = 0.25f)

@Composable
private fun PermissionScreen(onAllow: () -> Unit, onPickCity: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedSky(WeatherKind.PARTLY, isDay = true, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(PermissionScrim))
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Con letra grande o apaisado no entra: se desplaza en vez de cortarse.
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 32.dp, bottom = 32.dp + LocalBottomBarSpace.current),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(WeatherCodes.icon(2)),
                contentDescription = null,
                modifier = Modifier.size(140.dp),
            )
            Spacer(Modifier.height(24.dp))
            // El mismo vidrio que las tarjetas del pronóstico: el texto blanco se lee aunque el
            // cielo de día sea muy claro, y los botones no traen colores del tema sobre el cielo.
            SkyGlass(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tr("El clima donde estés", "The weather wherever you are"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = SkySurface.content,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr("Necesito tu ubicación aproximada para mostrarte el pronóstico y mantener los widgets al día.", "I need your approximate location to show the forecast and keep the widgets up to date."),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SkySurface.contentVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onAllow, colors = skyButtonColors(), modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Permitir ubicación", "Allow location"))
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onPickCity, colors = skyTonalButtonColors(), modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Elegir una ciudad", "Pick a city"))
                    }
                }
            }
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
    onOpenRadar: (Double, Double) -> Unit,
    shownAt: Long,
    deepLink: DeepLinkTarget?,
    onDeepLinkHandled: () -> Unit,
    onBarHiddenChange: (Boolean) -> Unit = {},
    scrollToTop: Int = 0,
    /** Con riel, la foto la dibuja la raíz a pantalla completa: acá no se dibuja. */
    photoBehind: Boolean = false,
    onPhotoChange: (RailPhoto?) -> Unit = {},
) {
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val loadPhase by viewModel.loadPhase.collectAsStateWithLifecycle()
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
    // Páginas que se están desplazando ahora.
    val scrolling = remember { mutableStateMapOf<String, Boolean>() }
    // Páginas que piden esconder la barra (se bajó en su lista): manda la que se mira.
    val hidesBar = remember { mutableStateMapOf<String, Boolean>() }
    val currentPages by rememberUpdatedState(pages)
    val reportBarHidden by rememberUpdatedState(onBarHiddenChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { hidesBar[currentPages.getOrElse(pagerState.currentPage) { "" }] == true }
            .collect { reportBarHidden(it) }
    }
    val skyId = pages.getOrElse(pagerState.currentPage) { CURRENT_PLACE_ID }
    val skySnapshot by remember(skyId) { viewModel.snapshots(skyId) }
        .collectAsStateWithLifecycle(viewModel.cached(skyId))
    // Mientras se desplaza, o con el encabezado ya arriba, el cielo casi no se ve detrás de las
    // tarjetas: va más lento y el desplazamiento queda más fluido. derivedStateOf: solo cambia al
    // empezar o terminar, no con cada píxel.
    val calmSky by remember(skyId) {
        derivedStateOf { scrolling[skyId] == true || collapsed[skyId] == true }
    }
    // Foto del lugar (Ajustes → Pantalla del clima → Fondo). Solo la de la ciudad que se mira y una
    // sola en memoria: con varias ciudades, una foto decodificada por página ocupaba demasiado.
    val photoOn = Appearance.backdrop == SkyBackdrop.PHOTO
    val context = LocalContext.current
    var shownPhoto by remember { mutableStateOf<ShownPhoto?>(null) }
    val photoLatitude = skySnapshot?.latitude
    val photoLongitude = skySnapshot?.longitude
    val photoPlace = skySnapshot?.place
    // Por celda de unos 5 km, como la caché: "donde estoy" no vuelve a buscar por moverse unos metros.
    val photoCell = if (photoLatitude != null && photoLongitude != null) {
        (photoLatitude * 20).roundToInt() to (photoLongitude * 20).roundToInt()
    } else {
        null
    }
    LaunchedEffect(photoOn, skyId, photoCell) {
        if (!photoOn) {
            shownPhoto = null
            return@LaunchedEffect
        }
        if (photoLatitude == null || photoLongitude == null) return@LaunchedEffect
        val photo = PlacePhotos.photoFor(context, photoLatitude, photoLongitude, photoPlace)
        // Nítida va al tamaño de la pantalla (la miniatura de Commons es de 1280 px de ancho).
        val metrics = view.resources.displayMetrics
        val bitmap = photo?.let { PlacePhotos.bitmap(it, metrics.widthPixels, metrics.heightPixels) }
        shownPhoto = if (photo != null && bitmap != null) ShownPhoto(skyId, photo, bitmap) else null
    }
    // Mientras baja, o si no hay foto libre cerca, queda el cielo animado.
    val backdrop = shownPhoto?.takeIf { photoOn && it.placeId == skyId }
    val skyKind = skySnapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY
    val skyIsDay = skySnapshot?.current?.isDay ?: true
    val railPhoto = backdrop?.let { RailPhoto(it, skyKind, skyIsDay, calmSky) }
    LaunchedEffect(railPhoto) { onPhotoChange(railPhoto) }
    // Al salir del pronóstico la raíz deja de dibujarla.
    DisposableEffect(Unit) { onDispose { onPhotoChange(null) } }

    Box(Modifier.fillMaxSize()) {
        // Fundido al llegar la foto, al cambiar de ciudad y al volver al cielo.
        Crossfade(targetState = backdrop?.takeIf { !photoBehind }, animationSpec = tween(700), modifier = Modifier.fillMaxSize(), label = "fondo") { shown ->
            if (shown == null && backdrop != null) {
                // La foto está detrás, dibujada por la raíz: acá nada.
            } else if (shown == null) {
                AnimatedSky(
                    kind = skyKind,
                    isDay = skyIsDay,
                    modifier = Modifier.fillMaxSize(),
                    southern = (skySnapshot?.latitude ?: 0.0) < 0,
                    // El ícono grande del pronóstico ya es el sol (o la luna): sin esto se veían dos.
                    sunMoon = false,
                    snapshot = skySnapshot,
                    calm = calmSky,
                )
            } else {
                PhotoBackdrop(shown.bitmap, Modifier.fillMaxSize()) {
                    // La lluvia y la nieve siguen sobre la foto, debajo del velo. La niebla no:
                    // su bruma blanca le quitaba contraste a las tarjetas.
                    if (skyKind in PhotoParticles) {
                        AnimatedSky(skyKind, skyIsDay, Modifier.fillMaxSize(), calm = calmSky, particlesOnly = true)
                    }
                }
            }
        }
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
                    val placeId = pages[page]
                    WeatherPage(
                        placeId = placeId,
                        viewModel = viewModel,
                        error = errors[placeId],
                        offline = offline,
                        // El esqueleto de "donde estoy" dice si busca la ubicación o baja el pronóstico.
                        loadingStatus = when {
                            placeId != CURRENT_PLACE_ID -> tr("Bajando el pronóstico…", "Getting the forecast…")
                            loadPhase == LoadPhase.LOCATING -> tr("Buscando tu ubicación…", "Finding your location…")
                            loadPhase == LoadPhase.DOWNLOADING -> tr("Bajando el pronóstico…", "Getting the forecast…")
                            else -> null
                        },
                        onOpenRadar = onOpenRadar,
                        onPickCity = onOpenCities,
                        openDay = pendingDay?.takeIf { it.first == placeId }?.second,
                        onDayOpened = { pendingDay = null },
                        onCollapsedChange = { collapsed[placeId] = it },
                        onScrollingChange = { scrolling[placeId] = it },
                        onHideBarChange = { hidesBar[placeId] = it },
                        scrollToTop = scrollToTop,
                        openAlert = openAlert,
                        shownAt = shownAt,
                        // Una sola ciudad: arriba no hay puntos y el encabezado puede subir.
                        compactTop = pages.size == 1,
                        photo = backdrop?.takeIf { it.placeId == placeId }?.photo,
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
                // Arriba de la barra flotante.
                .padding(bottom = LocalBottomBarSpace.current)
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
        // Sobre la foto no hay un color de cielo que tomar: el del vidrio.
        val barColor = if (backdrop != null) SkySurface.Tint else skyColors(skyKind, skyIsDay)[0]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Al bajar, la barra toma el color de arriba del cielo para que el texto se lea.
                .background(barColor.copy(alpha = 0.92f * barAlpha))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .heightIn(min = 48.dp),
        ) {
            CompactHeader(
                skySnapshot,
                Modifier
                    .align(Alignment.Center)
                    // El lugar del botón de compartir, de los dos lados: queda centrada de verdad.
                    .padding(horizontal = 48.dp)
                    .graphicsLayer {
                        alpha = barAlpha
                        translationY = (1f - barAlpha) * 12.dp.toPx()
                    }
                    // Escondida, TalkBack no la lee: el encabezado grande ya dice lo mismo.
                    .then(if (isCollapsed) Modifier else Modifier.clearAndSetSemantics { }),
            )
            if (pages.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { alpha = 1f - barAlpha }
                        .semantics {
                            contentDescription = tr(
                                "Ciudad ${pagerState.currentPage + 1} de ${pages.size}",
                                "City ${pagerState.currentPage + 1} of ${pages.size}",
                            )
                        },
                ) {
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
            ShareButton(skySnapshot, backdrop?.photo, Modifier.align(Alignment.CenterEnd))
        }
    }
}

/** La foto de fondo con lo necesario para dibujarla detrás del riel. */
data class RailPhoto(val shown: ShownPhoto, val kind: WeatherKind, val isDay: Boolean, val calm: Boolean)

/** Climas cuyas gotas, copos o relámpagos se dibujan encima de la foto del lugar. */
private val PhotoParticles = setOf(WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.STORM, WeatherKind.SNOW)

/** Compartir el pronóstico de la ciudad que se mira: abre la hoja con la vista previa y las opciones. */
@Composable
private fun ShareButton(snapshot: WeatherSnapshot?, photo: PlacePhoto?, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Sin pronóstico no hay qué dibujar.
    val enabled = snapshot != null
    IconButton(onClick = { open = true }, enabled = enabled, modifier = modifier) {
        Icon(
            Icons.Rounded.Share,
            contentDescription = tr("Compartir pronóstico", "Share forecast"),
            tint = OnSky.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
    if (open && snapshot != null) {
        ShareSheet(snapshot, photo, onDismiss = { open = false })
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
    offline: Boolean = false,
    loadingStatus: String? = null,
    onPickCity: () -> Unit = {},
    openDay: String? = null,
    onDayOpened: () -> Unit = {},
    onCollapsedChange: (Boolean) -> Unit = {},
    onScrollingChange: (Boolean) -> Unit = {},
    openAlert: String? = null,
    banner: (@Composable () -> Unit)? = null,
    shownAt: Long = 0L,
    compactTop: Boolean = false,
    onHideBarChange: (Boolean) -> Unit = {},
    scrollToTop: Int = 0,
    photo: PlacePhoto? = null,
) {
    val cards = viewModel.cards.collectAsStateWithLifecycle().value.visible
    val snapshot by remember(placeId) { viewModel.snapshots(placeId) }
        .collectAsStateWithLifecycle(viewModel.cached(placeId))
    val current = snapshot
    var selectedDay by remember { mutableStateOf<Day?>(null) }
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
        offline = offline,
        loadingStatus = loadingStatus,
        onPickCity = onPickCity,
        onScrollingChange = onScrollingChange,
        compactTop = compactTop,
        onHideBarChange = onHideBarChange,
        scrollToTop = scrollToTop,
        photo = photo,
    )
}

/** Ancho desde el que el pronóstico se divide en dos paneles (plegables abiertos y tablets). */
private val TwoPaneWidth = 600.dp

/** Desde este ancho (tablet apaisada) van tres columnas. */
private val ThreePaneWidth = 1100.dp

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
    offline: Boolean = false,
    loadingStatus: String? = null,
    onPickCity: () -> Unit = {},
    onScrollingChange: (Boolean) -> Unit = {},
    compactTop: Boolean = false,
    /** Si la barra de abajo debería esconderse (se bajó en la lista). Se avisa solo al cambiar. */
    onHideBarChange: (Boolean) -> Unit = {},
    /** Al cambiar (volver a tocar Clima), las listas suben al principio. */
    scrollToTop: Int = 0,
    /** La foto del lugar que se ve de fondo, si hay: el vidrio va al máximo y abajo va su crédito. */
    photo: PlacePhoto? = null,
) {
    // La lista del resumen (la única en pantallas angostas) decide si el encabezado se achica.
    val summaryState = rememberLazyListState()
    // Las otras columnas de los paneles: también cuentan para calmar el cielo al desplazar.
    val middleState = rememberLazyListState()
    val lastState = rememberLazyListState()
    val scrolling by remember {
        derivedStateOf { summaryState.isScrollInProgress || middleState.isScrollInProgress || lastState.isScrollInProgress }
    }
    LaunchedEffect(scrolling) { onScrollingChange(scrolling) }
    // Barra de abajo: al bajar pide esconderse y al subir, volver. Se acumula el recorrido en una
    // dirección (un temblor del dedo no la mueve) y, empujando más allá del final, vuelve.
    val barSlop = with(LocalDensity.current) { 24.dp.toPx() }
    val wantsBarHidden = remember { mutableStateOf(false) }
    val barScroll = remember(barSlop) {
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -0.5f) {
                    // Llegó al final y se sigue empujando: que se vea para poder ir a otra pestaña.
                    travel = 0f
                    wantsBarHidden.value = false
                } else if (consumed.y != 0f) {
                    travel = if (travel == 0f || consumed.y.sign == travel.sign) travel + consumed.y else consumed.y
                    if (travel < -barSlop) wantsBarHidden.value = true else if (travel > barSlop) wantsBarHidden.value = false
                }
                return Offset.Zero
            }
        }
    }
    // Arriba de todo siempre se ve. snapshotFlow: se avisa solo al cambiar, sin recomponer el
    // pronóstico con cada píxel.
    val reportHideBar by rememberUpdatedState(onHideBarChange)
    LaunchedEffect(Unit) {
        snapshotFlow {
            wantsBarHidden.value &&
                (summaryState.canScrollBackward || middleState.canScrollBackward || lastState.canScrollBackward)
        }.collect { reportHideBar(it) }
    }
    // Lo que ya valía al armarse no cuenta: si no, al volver de otra pestaña subiría solo.
    val initialScrollToTop = remember { scrollToTop }
    LaunchedEffect(scrollToTop) {
        if (scrollToTop != initialScrollToTop) {
            // Solo las listas que están en pantalla y bajaron: las otras no tienen a dónde subir.
            listOf(summaryState, middleState, lastState).forEach { state ->
                if (state.canScrollBackward) launch { state.animateScrollToItem(0) }
            }
        }
    }
    // Un reloj por minuto: "Actualizado hace…", el aviso de datos viejos y la lluvia de la
    // próxima hora siguen al día aunque la pantalla quede abierta.
    val minute by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val age = snapshot?.let { minute - it.fetchedAt }
    // Con datos de menos de media hora, una actualización fallida o la falta de red no se avisan:
    // lo que se ve sigue sirviendo.
    val oldish = age != null && age > 30 * 60_000L
    // Datos de hace más de 2 horas: se avisa arriba, no solo en la letra chica del final.
    val stale = age != null && age > 2 * 60 * 60_000L
    // Se calculan una vez por pronóstico (y por minuto), no en cada recomposición.
    val alerts = remember(snapshot, minute) { snapshot?.alerts.orEmpty().distinctBy { it.id }.filterNot { it.isExpired() } }
    val nextRain = remember(snapshot, minute) { snapshot?.let(::nextHourRainForCard) }
    val headline = remember(snapshot, minute) { snapshot?.let { Insights.headline(it.copy(alerts = emptyList())) } }
    val ageLabel = remember(snapshot, minute) { snapshot?.let { ageText(it.fetchedAt) } }
    // El aviso de arriba: sin red, en tono tranquilo; con error, el de error; si no, solo si es viejo.
    val status: StatusNotice? = when {
        snapshot == null -> null
        offline && oldish -> StatusNotice(tr("Sin conexión · datos de ", "No connection · data from ") + ageLabel, null, isError = false, canRetry = false)
        error != null && oldish -> StatusNotice(error, ageLabel?.let { tr("Mostrando datos de ", "Showing data from ") + it }, isError = true, canRetry = true)
        stale -> StatusNotice(null, ageLabel?.let { tr("Mostrando datos de ", "Showing data from ") + it }, isError = false, canRetry = true)
        else -> null
    }
    val heroIndex = listOfNotNull(banner, status).size
    val collapseAt = with(LocalDensity.current) { 180.dp.roundToPx() }
    val isCollapsed by remember(heroIndex) {
        derivedStateOf {
            summaryState.firstVisibleItemIndex > heroIndex ||
                (summaryState.firstVisibleItemIndex == heroIndex && summaryState.firstVisibleItemScrollOffset > collapseAt)
        }
    }
    LaunchedEffect(isCollapsed) { onCollapsedChange(isCollapsed) }
    val insets = WindowInsets.systemBars.asPaddingValues()
    val compact = Appearance.compactLayout
    // Lugar de la fila de arriba (puntos de ciudades y compartir).
    val topBarSpace = 56.dp
    // Con una sola ciudad ([compactTop]) no hay puntos y, en una columna, el encabezado sube. No
    // llega a los 16 dp: el ícono grande del clima quedaba debajo del botón de compartir. Con un
    // aviso arriba tampoco: su "Reintentar" caería bajo ese botón.
    val singleTopSpace = if (compactTop && heroIndex == 0) 32.dp else topBarSpace
    val padding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = insets.calculateTopPadding() + singleTopSpace,
        bottom = insets.calculateBottomPadding() + 24.dp + LocalBottomBarSpace.current,
    )
    val spacing = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
    // La frase va como encabezado de "Próximas horas", como el resumen del Clima de Apple; si esa
    // tarjeta está oculta, va sola arriba.
    val headlineInHours = ForecastCard.HOURS in cards
    // Cuán claro es el cielo de este lugar ahora: de eso dependen el velo y el vidrio de las tarjetas.
    val skyBrightness = remember(snapshot, minute, photo) {
        if (photo != null) {
            // Sobre una foto no se sabe cuán clara es: más que el cielo más claro, así el vidrio es
            // más opaco que nunca y las tarjetas mandan aunque la foto se vea más.
            SkySurface.PHOTO_BRIGHTNESS
        } else {
            val kind = snapshot?.current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY
            SkySurface.brightness(SkyTime.colors(kind, snapshot?.current?.isDay ?: true, snapshot))
        }
    }
    // El mapa tiene su pestaña: acá la tarjeta va solo si hay lluvia cerca o en la región. La
    // vista previa se pide acá (y no dentro de la tarjeta) para no dejar un ítem vacío en la lista;
    // mientras no llega, no hay tarjeta. Se vuelve a mirar cada 15 minutos, lo que dura su caché.
    val darkMap = LocalDarkTheme.current
    val radarLatitude = snapshot?.latitude
    val radarLongitude = snapshot?.longitude
    val wantsRadar = ForecastCard.RADAR in cards
    val radarPreview by produceState<RainMap.Preview?>(null, radarLatitude, radarLongitude, darkMap, wantsRadar, minute / 900_000) {
        value = if (wantsRadar && radarLatitude != null && radarLongitude != null) {
            runCatching { RainMap.preview(radarLatitude, radarLongitude, darkMap) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                .getOrNull()
        } else {
            null
        }
    }
    val radarShown = radarPreview?.takeIf { it.nearby || it.inArea }

    // Cada bloque es una función que agrega ítems a una lista; así se reparten entre uno o dos paneles.
    val summary: LazyListScope.() -> Unit = {
        if (banner != null) item(key = "aviso", contentType = "aviso") { banner() }
        status?.let { notice ->
            item(key = "estado", contentType = "estado") {
                ErrorBanner(notice, onRetry = onRetry)
            }
        }
        if (snapshot == null) {
            if (error != null || offline) {
                // Sin nada guardado y sin poder bajarlo: una pantalla que explica y ofrece salidas,
                // no un aviso suelto sobre el cielo vacío.
                item(key = "vacio", contentType = "vacio") {
                    EmptyForecast(
                        message = if (offline) {
                            tr(
                                "Sin conexión. Cuando vuelva la red, el pronóstico se baja solo.",
                                "No connection. The forecast will download when you're back online.",
                            )
                        } else {
                            error.orEmpty()
                        },
                        onRetry = onRetry,
                        onPickCity = onPickCity,
                    )
                }
            } else {
                item(key = "esqueleto", contentType = "esqueleto") { ForecastSkeleton(loadingStatus) }
            }
        } else {
            item(key = "encabezado", contentType = "encabezado") {
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
            items(alerts, key = { "alerta-" + it.id }, contentType = { "alerta" }) { alert ->
                Box(Modifier.reveal(1, shownAt)) { AlertCard(alert, startExpanded = alert.id == openAlert) }
            }
            nextRain?.let { values ->
                item(key = "proxima-hora", contentType = "proxima-hora") {
                    Box(Modifier.reveal(1, shownAt)) { NextHourRainCard(snapshot, values) }
                }
            }
            // Las alertas ya tienen su tarjeta: la frase busca lo siguiente más útil.
            if (!headlineInHours) {
                headline?.let { text ->
                    item(key = "frase", contentType = "frase") {
                        Box(Modifier.reveal(1, shownAt)) {
                            GlassCard(title = null) {
                                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    // Cada tarjeta, en el orden que eligió la persona (Ajustes → Pantalla del clima).
    // En paneles, los detalles se pueden repartir en dos mitades ([part]).
    val card: LazyListScope.(ForecastCard, Int, DetailsPart) -> Unit = { which, index, part ->
        if (snapshot != null) {
            item(key = which.name + part.name, contentType = which.name) {
                Box(Modifier.reveal(index, shownAt)) {
                    when (which) {
                        ForecastCard.HOURS -> GlassCard(tr("Próximas horas", "Next hours"), summary = headline) {
                            // Con la hora en la clave: si la pantalla queda abierta, el gráfico avanza solo.
                            val hourKey = minute / 3_600_000
                            val sunEvents = remember(snapshot, hourKey) { sunEventsOf(snapshot.days).filter { it.time.isAfter(snapshot.nowAtPlace()) } }
                            val hours = remember(snapshot, hourKey) { snapshot.upcomingHours(24) }
                            val yesterday = remember(snapshot) { snapshot.yesterdayAt(hours) }
                            val alertSpans = remember(snapshot) { alertSpans(snapshot) }
                            HourlyChart(hours, sunEvents = sunEvents, yesterday = yesterday, alerts = alertSpans)
                        }
                        ForecastCard.DAYS -> DailyCard(snapshot, onDay = onDay, shownAt = shownAt)
                        ForecastCard.RADAR -> radarShown?.let { preview ->
                            RadarCard(preview, onClick = { onOpenRadar(snapshot.latitude, snapshot.longitude) })
                        }
                        ForecastCard.DETAILS -> DetailsGrid(snapshot, part = part, minute = minute / 60_000)
                    }
                }
            }
        }
    }
    val footer: LazyListScope.() -> Unit = {
        if (snapshot != null) {
            item(key = "pie", contentType = "pie") {
                // En su propio vidrio: suelto sobre el horizonte claro de un día despejado no se leía.
                Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    SkyGlass(shape = ClimaShapes.inner) {
                        Text(
                            tr("Actualizado", "Updated") + " $ageLabel (${updatedAt(snapshot.fetchedAt)}) · " + tr("Datos de Open-Meteo", "Data by Open-Meteo"),
                            style = MaterialTheme.typography.labelMedium,
                            color = SkySurface.contentVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            photo?.let { shown ->
                item(key = "foto", contentType = "foto") { PhotoCredit(shown) }
            }
        }
    }
    // Las tarjetas elegidas, en su orden, que van en una columna: [pick] dice si va y con qué parte.
    fun LazyListScope.cardsWhere(pick: (ForecastCard) -> DetailsPart?) {
        cards.forEachIndexed { i, which ->
            // Sin lluvia a la vista (o sin vista previa todavía) la tarjeta del mapa no va.
            if (which == ForecastCard.RADAR && radarShown == null) return@forEachIndexed
            pick(which)?.let { part -> card(which, 2 + i, part) }
        }
    }

    CompositionLocalProvider(LocalSkyBrightness provides skyBrightness) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .nestedScroll(barScroll)
            // Velo oscuro: fuerte arriba, donde la temperatura va directo sobre el cielo, y en toda
            // la lista según cuán claro es el cielo, para que el blanco de las tarjetas se lea.
            .background(SkySurface.veil(skyBrightness)),
    ) {
        val paneSpacing = if (compact) 8.dp else 12.dp
        // Cada panel con su relleno: entre paneles va la mitad, así el hueco es parejo.
        fun panePadding(first: Boolean, last: Boolean) = PaddingValues(
            start = if (first) 16.dp else paneSpacing / 2,
            end = if (last) 16.dp else paneSpacing / 2,
            // En paneles la fila de arriba siempre tiene su lugar: el botón de compartir cae
            // sobre la columna derecha, que arranca con una tarjeta.
            top = insets.calculateTopPadding() + topBarSpace,
            bottom = padding.calculateBottomPadding(),
        )
        when {
            // Sin pronóstico todavía (cargando o con error) en pantalla ancha: una columna
            // centrada. Repartido en paneles, el esqueleto o el aviso ocupaban solo el de la
            // izquierda y el resto quedaba vacío.
            snapshot == null && maxWidth >= TwoPaneWidth -> LazyColumn(
                state = summaryState,
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = TwoPaneWidth),
                contentPadding = padding,
                verticalArrangement = spacing,
            ) {
                summary()
            }
            // Tablet apaisada: el ahora, lo que viene y los detalles, en tres columnas.
            maxWidth >= ThreePaneWidth -> Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = summaryState,
                    modifier = Modifier.weight(0.31f).fillMaxSize(),
                    contentPadding = panePadding(first = true, last = false),
                    verticalArrangement = spacing,
                ) {
                    summary()
                    cardsWhere { if (it == ForecastCard.RADAR) DetailsPart.ALL else null }
                }
                LazyColumn(
                    state = middleState,
                    modifier = Modifier.weight(0.37f).fillMaxSize(),
                    contentPadding = panePadding(first = false, last = false),
                    verticalArrangement = spacing,
                ) {
                    cardsWhere { if (it == ForecastCard.HOURS || it == ForecastCard.DAYS) DetailsPart.ALL else null }
                    footer()
                }
                LazyColumn(
                    state = lastState,
                    modifier = Modifier.weight(0.32f).fillMaxSize(),
                    contentPadding = panePadding(first = false, last = true),
                    verticalArrangement = spacing,
                ) {
                    cardsWhere { if (it == ForecastCard.DETAILS) DetailsPart.ALL else null }
                }
            }
            // Plegable abierto o tablet: dos columnas parejas. Los detalles se reparten entre las
            // dos (antes iban todos a la izquierda y la derecha quedaba corta).
            maxWidth >= TwoPaneWidth -> Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = summaryState,
                    modifier = Modifier.weight(0.46f).fillMaxSize(),
                    contentPadding = panePadding(first = true, last = false),
                    verticalArrangement = spacing,
                ) {
                    summary()
                    cardsWhere {
                        when (it) {
                            ForecastCard.RADAR -> DetailsPart.ALL
                            ForecastCard.DETAILS -> DetailsPart.FIRST
                            else -> null
                        }
                    }
                }
                LazyColumn(
                    state = middleState,
                    modifier = Modifier.weight(0.54f).fillMaxSize(),
                    contentPadding = panePadding(first = false, last = true),
                    verticalArrangement = spacing,
                ) {
                    cardsWhere {
                        when (it) {
                            ForecastCard.HOURS, ForecastCard.DAYS -> DetailsPart.ALL
                            ForecastCard.DETAILS -> DetailsPart.SECOND
                            else -> null
                        }
                    }
                    footer()
                }
            }
            else -> LazyColumn(
                state = summaryState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = spacing,
            ) {
                summary()
                cardsWhere { DetailsPart.ALL }
                footer()
            }
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

/** Lo que dice el aviso de arriba del pronóstico: qué pasó, de cuándo son los datos y si se puede reintentar. */
private data class StatusNotice(val message: String?, val age: String?, val isError: Boolean, val canRetry: Boolean)

/**
 * No se pudo actualizar, no hay red o lo que se ve es viejo: qué pasó, de cuándo son los datos que
 * se ven y un botón para reintentar. TalkBack lo anuncia al aparecer.
 */
@Composable
private fun ErrorBanner(notice: StatusNotice, onRetry: () -> Unit) {
    val isError = notice.isError
    val message = notice.message
    val age = notice.age
    // El mismo vidrio que las tarjetas; el error se nota en el color del ícono, no en un fondo opaco.
    SkyGlass(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        strong = isError,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isError) Icons.Rounded.Warning else Icons.Rounded.Info,
                contentDescription = null,
                tint = if (isError) SkySurface.severe else SkySurface.contentVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                age?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (message == null) SkySurface.content else SkySurface.contentVariant) }
            }
            // Sin red no tiene sentido: se reintenta solo cuando vuelve.
            if (notice.canRetry) {
                // El color primario del tema no se lee sobre el vidrio oscuro.
                TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = SkySurface.content)) {
                    Text(tr("Reintentar", "Retry"))
                }
            } else {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Sin pronóstico guardado y sin poder bajarlo: qué pasa y qué se puede hacer. */
@Composable
private fun EmptyForecast(message: String, onRetry: () -> Unit, onPickCity: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(WeatherCodes.icon(3)),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            tr("No pude traer el pronóstico", "Couldn't get the forecast"),
            style = MaterialTheme.typography.headlineSmall.onSky(),
            color = OnSky,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge.onSky(),
            color = OnSkyVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(tr("Reintentar", "Retry")) }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onPickCity) { Text(tr("Elegir una ciudad", "Pick a city")) }
    }
}

/** Temperatura grande directo sobre el cielo, como la app de clima del Pixel. */
@Composable
private fun Hero(snapshot: WeatherSnapshot, isCurrentLocation: Boolean) {
    val current = snapshot.current
    val today = snapshot.today()
    val compact = Appearance.compactLayout
    // Ajustes → Pantalla del clima: tamaño del número y qué datos van debajo.
    val scale = Appearance.heroSize.scale * if (compact) 0.85f else 1f
    val lines = Appearance.heroLines
    // El ícono va arriba a la derecha, a la altura del nombre, donde el cielo tenía su sol.
    val iconSize = 112.dp * scale.coerceAtMost(1.1f)
    Box(Modifier.padding(horizontal = 8.dp, vertical = if (compact) 0.dp else 8.dp)) {
    AnimatedWeatherIcon(
        current.code,
        current.isDay,
        size = iconSize,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(y = (-16).dp),
    )
    Column {
        Row(Modifier.padding(end = iconSize * 0.8f), verticalAlignment = Alignment.CenterVertically) {
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
        // Sin la fecha, como Apple y el Pixel: en un teléfono chico era un renglón que empujaba
        // las próximas horas fuera de la primera pantalla.
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
                // Se deja lugar abajo del ícono: el número no se le mete encima.
                val available = constraints.maxWidth - with(LocalDensity.current) { (iconSize * 0.45f).toPx() }
                val fit = (available / measured.coerceAtLeast(1)).coerceAtMost(1f) * 0.97f
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
                        // Letra más chica que el renglón de arriba: va en blanco pleno.
                        color = OnSky,
                    )
                }
            }
            }
        }
    }
    }
}

/** Los datos elegidos para debajo de la temperatura, salvo máxima y mínima (que van en su renglón). */
private fun heroExtras(snapshot: WeatherSnapshot, lines: Set<HeroLine>): List<String> {
    val current = snapshot.current
    val today = snapshot.today()
    // Si el pronóstico decía otra cosa y la lluvia la vio el radar, se dice de dónde sale.
    val radar = listOfNotNull(tr("Lloviendo según el radar", "Raining, per the radar").takeIf { snapshot.rainByRadar })
    return radar + HeroLine.entries.filter { it in lines }.mapNotNull { line ->
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

/**
 * Alerta oficial, corta: el aviso y una línea del titular, para que varias alertas no tapen el
 * pronóstico. Tocarla muestra u oculta el detalle completo.
 */
@Composable
private fun AlertCard(alert: WeatherAlert, startExpanded: Boolean = false) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(startExpanded) }
    // Si llega desde la notificación con la tarjeta ya en pantalla, se abre igual.
    LaunchedEffect(startExpanded) { if (startExpanded) expanded = true }
    val severe = alert.isSevere
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "flecha alerta")
    // El nivel va como acento (ícono y título) sobre el mismo vidrio, un poco más oscuro para que
    // se destaque; antes era un fondo opaco de otro material.
    val accent = if (severe) SkySurface.severe else SkySurface.warning
    SkyGlass(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.card)
            .pressable { expanded = !expanded }
            .semantics { stateDescription = if (expanded) tr("Abierta", "Expanded") else tr("Cerrada, tocá para ver el detalle", "Collapsed, tap for details") }
            .animateContentSize(Motion.gentle()),
        strong = true,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        alert.event.calmCase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    alert.headline?.let {
                        Text(
                            it.calmCase(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = SkySurface.contentVariant,
                    modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevron },
                )
            }
            if (expanded) {
                alert.description?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tr("Fuente: Servicio Meteorológico Nacional", "Source: Argentina's National Weather Service"),
                    style = MaterialTheme.typography.labelSmall,
                    color = SkySurface.contentVariant,
                )
            }
        }
    }
}

/**
 * El SMN escribe muchos avisos en mayúsculas ("AVISO NARANJA POR TORMENTAS…"): gritaban. Si el
 * texto viene todo en mayúsculas, queda con mayúscula solo al principio.
 */
private fun String.calmCase(): String {
    val letters = filter { it.isLetter() }
    if (letters.isEmpty() || letters.any { it.isLowerCase() }) return this
    return lowercase().replaceFirstChar { it.uppercase() }
}

/** El mapa de lluvia en chico. Solo se muestra con lluvia cerca o en la región (lo decide quien la llama). */
@Composable
private fun RadarCard(preview: RainMap.Preview, onClick: () -> Unit) {
    val markerColor = MaterialTheme.colorScheme.primary
    SkyGlass(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ClimaShapes.card)
            .pressable(onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(
                Modifier
                    .size(72.dp)
                    .clip(ClimaShapes.inner),
            ) {
                val side = size.width
                preview.tiles.forEach { (xy, image) ->
                    drawImage(
                        image,
                        dstOffset = IntOffset(((xy[0] - preview.x0) * side).roundToInt(), ((xy[1] - preview.y0) * side).roundToInt()),
                        dstSize = IntSize(side.roundToInt() + 1, side.roundToInt() + 1),
                    )
                }
                preview.rain?.let {
                    drawImage(it, dstSize = IntSize(side.roundToInt(), side.roundToInt()), filterQuality = FilterQuality.High)
                }
                val center = Offset(side / 2, size.height / 2)
                drawCircle(Color.White, 5.dp.toPx(), center)
                drawCircle(markerColor, 3.5.dp.toPx(), center)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(tr("Mapa de lluvia", "Rain map"), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (preview.nearby) {
                        tr("Llueve cerca ahora. Tocá para ver cómo se mueve", "It's raining nearby. Tap to see where it's going")
                    } else {
                        tr("Hay lluvia en la región, no acá. Tocá para ver hacia dónde va", "Rain in the region, not here. Tap to see where it's going")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkySurface.contentVariant,
                )
            }
        }
    }
}

/**
 * Tarjeta semitransparente sobre el cielo animado. Con [summary], una frase arriba con una línea
 * fina debajo, como el resumen de las próximas horas del Clima de Apple; reemplaza al título.
 */
@Composable
fun GlassCard(title: String?, summary: String? = null, content: @Composable () -> Unit) {
    SkyGlass(Modifier.fillMaxWidth()) {
        val compact = Appearance.compactLayout
        Column(Modifier.padding(vertical = if (compact) 12.dp else 16.dp)) {
            if (summary != null) {
                Text(summary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp))
                Box(
                    Modifier
                        .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(surfaceInk().divider),
                )
            } else if (title != null) {
                CardLabel(title, Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            }
            content()
        }
    }
}

/** Anchos de las columnas de una fila de día, medidos del texto: con letra grande no se cortan. */
private class DayColumns(val label: Dp, val mark: Dp, val temperature: Dp)

@Composable
private fun DailyCard(snapshot: WeatherSnapshot, onDay: (Day) -> Unit, shownAt: Long = 0L) {
    // Compacto: una semana en vez de dos.
    val compact = Appearance.compactLayout
    val days = snapshot.upcomingDays(if (compact) 7 else 14)
    if (days.isEmpty()) return
    val weekMin = days.minOf { it.min }
    val weekMax = days.maxOf { it.max }
    val today = snapshot.nowAtPlace().toLocalDate()
    val labels = days.map { dayLabel(it.localDate, today) }
    // Cada columna toma el ancho de su texto más largo (con un mínimo): todas las filas quedan
    // alineadas y, con la letra agrandada, "Mañana" o "−12°" no se cortan.
    val temperatures = days.flatMap { listOf(it.min.degrees(), it.max.degrees()) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.bodyLarge
    val temperatureStyle = MaterialTheme.typography.bodyMedium
    val markStyle = MaterialTheme.typography.labelMedium
    val columns = remember(labels, temperatures, labelStyle, temperatureStyle, markStyle, density) {
        fun widest(texts: List<String>, style: TextStyle): Dp =
            with(density) { texts.maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }.toDp() }
        DayColumns(
            label = (widest(labels, labelStyle) + 8.dp).coerceAtLeast(72.dp),
            mark = (widest(listOf("100%"), markStyle) + 8.dp).coerceAtLeast(40.dp),
            temperature = (widest(temperatures, temperatureStyle) + 4.dp).coerceAtLeast(36.dp),
        )
    }
    val todayBackground = surfaceInk().highlight
    GlassCard(tr("Próximos ${days.size} días", "Next ${days.size} days")) {
        // Compacto junta las filas; la fila en sí no baja de 48 dp, que es lo que se toca.
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 2.dp)) {
            days.forEachIndexed { index, day ->
                val isToday = day.localDate == today
                DayRow(
                    day,
                    labels[index],
                    weekMin,
                    weekMax,
                    columns,
                    now = snapshot.current.temperature.takeIf { isToday },
                    shownAt = shownAt,
                    modifier = Modifier
                        .clip(ClimaShapes.inner)
                        // Hoy se destaca con un fondo suave, como en el clima del Pixel.
                        .background(if (isToday) todayBackground else Color.Transparent)
                        .pressable { onDay(day) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = if (compact) 2.dp else 6.dp),
                )
            }
        }
    }
}

@Composable
private fun DayRow(
    day: Day,
    label: String,
    weekMin: Double,
    weekMax: Double,
    columns: DayColumns,
    now: Double? = null,
    shownAt: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val ink = surfaceInk()
    val probability = day.precipitationProbability ?: 0
    // Sin lluvia, el lugar avisa de viento fuerte o sol muy fuerte.
    val windy = (day.windGustsMax ?: 0.0) >= 50 || (day.windMax ?: 0.0) >= 35
    val strongSun = (day.uvIndex ?: 0.0) >= 8
    val spoken = buildString {
        append("$label: ${WeatherCodes.description(day.code)}, ")
        append(tr("máxima", "high") + " ${day.max.degrees()}, " + tr("mínima", "low") + " ${day.min.degrees()}")
        if (now != null) append(", " + tr("ahora", "now") + " ${now.degrees()}")
        if (probability >= 10) append(", " + tr("lluvia", "rain") + " $probability%")
        if (windy) append(", " + tr("con viento", "windy"))
        if (strongSun) append(", " + tr("sol fuerte", "strong sun"))
    }
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = spoken
            role = Role.Button
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.width(columns.label))
        Image(
            painter = painterResource(WeatherCodes.icon(day.code)),
            contentDescription = WeatherCodes.description(day.code),
            modifier = Modifier.size(28.dp),
        )
        Box(Modifier.width(columns.mark), contentAlignment = Alignment.Center) {
            when {
                // Celeste fijo: el terciario del tema cambiaba con el color dinámico.
                probability >= 10 -> Text("$probability%", style = MaterialTheme.typography.labelMedium, color = ink.rain, maxLines = 1)
                windy -> Icon(WindIcon, contentDescription = null, tint = ink.variant, modifier = Modifier.size(18.dp))
                strongSun -> Icon(SunIcon, contentDescription = null, tint = ink.sun, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            day.min.degrees(),
            style = MaterialTheme.typography.bodyMedium,
            color = ink.variant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(columns.temperature),
        )
        TemperatureRange(
            min = day.min,
            max = day.max,
            weekMin = weekMin,
            weekMax = weekMax,
            now = now,
            shownAt = shownAt,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
        )
        Text(
            day.max.degrees(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.width(columns.temperature),
        )
    }
}

/**
 * Barra con el rango del día dentro del rango de la semana, como en el clima del Pixel.
 * En la fila de hoy, un punto marca la temperatura de ahora.
 */
@Composable
private fun TemperatureRange(min: Double, max: Double, weekMin: Double, weekMax: Double, now: Double?, shownAt: Long, modifier: Modifier) {
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    val start = ((min - weekMin) / span).toFloat().coerceIn(0f, 1f)
    val end = ((max - weekMin) / span).toFloat().coerceIn(start, 1f)
    val ink = surfaceInk()
    // Las barras crecen solo al abrir la app (el primer segundo y medio desde [shownAt], igual que
    // la entrada de las tarjetas): al volver de otra pestaña o al desplazar ya están quietas.
    val animate = remember { SystemClock.uptimeMillis() - shownAt < 1_500 }
    var grown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { grown = true }
    val grow by animateFloatAsState(if (grown) 1f else 0f, tween(700, delayMillis = 250, easing = EaseOutCubic), label = "rango")
    val onDark = ink.onDark
    val rangeColors = remember(min, max, onDark) { TempColors.between(min, max).map { TempColors.forTheme(it, onDark) } }
    val nowAt = now?.let { ((it - weekMin) / span).toFloat().coerceIn(0f, 1f) }
    val dotColor = ink.content
    val ringColor = ink.knockout
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
            .background(ink.track),
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
