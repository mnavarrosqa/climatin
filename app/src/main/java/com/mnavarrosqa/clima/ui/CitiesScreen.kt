package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnavarrosqa.clima.data.LocationProvider
import com.mnavarrosqa.clima.data.Updates
import com.mnavarrosqa.clima.data.PressureUnit
import com.mnavarrosqa.clima.data.TemperatureUnit
import com.mnavarrosqa.clima.data.WindUnit
import com.mnavarrosqa.clima.wallpaper.WallpaperSettingsActivity
import com.mnavarrosqa.clima.widget.WidgetGalleryActivity
import com.mnavarrosqa.clima.work.RainNotifier
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitiesScreen(
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    onLocationPermission: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val places by viewModel.places.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var hasLocation by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    // El permiso se puede cambiar desde Ajustes del sistema: se relee al volver.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocation = LocationProvider.hasPermission(context)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasLocation = it
        onLocationPermission(it)
    }

    LaunchedEffect(query) {
        delay(350)
        viewModel.searchCities(query)
    }

    val listState = rememberLazyListState()
    val reorder = remember { CityReorder(listState).apply { order = places.map { it.id } } }
    // Mientras no se arrastra, el orden es el guardado.
    LaunchedEffect(places) { if (reorder.dragging == null) reorder.order = places.map { it.id } }
    val finishDrag: () -> Unit = {
        reorder.end()?.let { (id, moved) -> viewModel.movePlace(id, moved) }
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(tr("Ciudades", "Cities")) },
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
            state = listState,
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
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("Buscar una ciudad", "Search for a city")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }
            when {
                search.loading -> item { Hint(tr("Buscando…", "Searching…")) }
                search.error != null -> item { Hint(search.error.orEmpty()) }
                query.trim().length >= 2 && search.results.isEmpty() -> item { Hint(tr("No encontré esa ciudad", "City not found")) }
            }
            items(search.results, key = { "r-" + it.id }) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = place.detail?.let { detail -> @Composable { Text(detail) } },
                    leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    trailingContent = { Icon(Icons.Rounded.Add, contentDescription = tr("Agregar", "Add")) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable {
                        viewModel.addPlace(place)
                        query = ""
                    },
                )
            }

            item { SectionTitle(tr("Tus ciudades", "Your cities")) }
            item(key = CURRENT_PLACE_ID) {
                val here by remember { viewModel.snapshots(CURRENT_PLACE_ID) }
                    .collectAsStateWithLifecycle(viewModel.cached(CURRENT_PLACE_ID))
                CityCard(
                    name = here?.place ?: tr("Donde estoy", "My location"),
                    detail = if (hasLocation) tr("Según tu ubicación", "Based on your location") else tr("Sin permiso de ubicación", "No location permission"),
                    snapshot = here,
                    icon = Icons.Rounded.LocationOn,
                    action = if (hasLocation) null else {
                        {
                            FilledTonalButton(onClick = {
                                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }) { Text(tr("Permitir", "Allow")) }
                        }
                    },
                )
            }
            if (places.isNotEmpty()) {
                item { Hint(tr("Deslizá una ciudad para quitarla; mantenela apretada para moverla.", "Swipe a city to remove it; press and hold to move it.")) }
            }
            items(reorder.order, key = { it }) { id ->
                val place = places.firstOrNull { it.id == id } ?: return@items
                val isDragging = reorder.dragging == id
                val snapshot by remember(id) { viewModel.snapshots(id) }
                    .collectAsStateWithLifecycle(viewModel.cached(id))
                val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp, label = "levantar")
                val dismiss = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value != SwipeToDismissBoxValue.Settled) {
                            // El estado se recuerda entre composiciones: se lee la lista actual.
                            val current = viewModel.places.value
                            val index = current.indexOfFirst { it.id == id }
                            val remaining = current.size - 1
                            viewModel.removePlace(id)
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    tr("Quitaste ${place.name}", "Removed ${place.name}"),
                                    actionLabel = tr("Deshacer", "Undo"),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.addPlace(place)
                                    // Vuelve al final: se lo sube al lugar que tenía.
                                    viewModel.movePlace(id, index - remaining)
                                }
                            }
                            true
                        } else {
                            false
                        }
                    },
                )
                val itemModifier = if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationY = reorder.offset
                            scaleX = 1.03f
                            scaleY = 1.03f
                        }
                } else {
                    Modifier.animateItem()
                }
                SwipeToDismissBox(
                    state = dismiss,
                    modifier = itemModifier.padding(top = 8.dp),
                    backgroundContent = {
                        val toStart = dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 24.dp),
                            contentAlignment = if (toStart) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    },
                ) {
                    CityCard(
                        name = place.name,
                        detail = place.detail,
                        snapshot = snapshot,
                        modifier = Modifier
                            .shadow(elevation, RoundedCornerShape(24.dp))
                            .pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        reorder.start(id)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (reorder.drag(amount.y)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    },
                                    onDragEnd = { finishDrag() },
                                    onDragCancel = { finishDrag() },
                                )
                            }
                            // Lectores de pantalla: subir, bajar y quitar como acciones.
                            .semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(tr("Subir", "Move up")) { viewModel.movePlace(id, -1); true },
                                    CustomAccessibilityAction(tr("Bajar", "Move down")) { viewModel.movePlace(id, 1); true },
                                    CustomAccessibilityAction(tr("Quitar", "Remove")) { viewModel.removePlace(id); true },
                                )
                            },
                    )
                }
            }

            item {
                Hint(tr("Las alertas, la ubicación con la app cerrada, las unidades y lo demás están en Ajustes (el engranaje, arriba a la derecha del pronóstico).", "Alerts, background location, units and everything else are in Settings (the gear, top right of the forecast)."))
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
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        content = content,
    )
}

@Composable
private fun groupColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
