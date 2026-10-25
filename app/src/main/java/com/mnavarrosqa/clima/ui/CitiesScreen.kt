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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.mnavarrosqa.clima.data.shortHour
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
import com.mnavarrosqa.clima.data.Place
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitiesScreen(
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    onLocationPermission: (Boolean) -> Unit,
    onOpenPlace: (String) -> Unit = {},
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
    // Ciudad a la que se le está poniendo apodo.
    /** Quita la ciudad con "Deshacer", igual al deslizar que desde TalkBack. */
    fun removeWithUndo(id: String) {
        // Se lee la lista actual (el estado de deslizar se recuerda entre composiciones).
        val current = viewModel.places.value
        val place = current.firstOrNull { it.id == id } ?: return
        val index = current.indexOf(place)
        val remaining = current.size - 1
        viewModel.removePlace(id)
        scope.launch {
            val result = snackbar.showSnackbar(
                tr("Quitaste ${place.shownName}", "Removed ${place.shownName}"),
                actionLabel = tr("Deshacer", "Undo"),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.addPlace(place)
                // Vuelve al final: se lo sube al lugar que tenía.
                viewModel.movePlace(id, index - remaining)
            }
        }
    }

    // Se guarda el id (no la ciudad) para que el diálogo sobreviva a girar la pantalla.
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    val renaming = renamingId?.let { id -> places.firstOrNull { it.id == id } }
    renaming?.let { place ->
        RenameDialog(
            place,
            onDismiss = { renamingId = null },
            onSave = { nickname ->
                viewModel.renamePlace(place.id, nickname)
                renamingId = null
            },
        )
    }

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
            // Buscador relleno y sin borde, como el de iOS.
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    placeholder = { Text(tr("Buscar una ciudad", "Search for a city")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) null else {
                        {
                            TextButton(onClick = { query = "" }) { Text(tr("Cancelar", "Cancel")) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
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

            // Mientras se busca, solo los resultados (como en el Clima de Apple).
            if (query.isBlank()) {
            item(key = CURRENT_PLACE_ID) {
                val here by remember { viewModel.snapshots(CURRENT_PLACE_ID) }
                    .collectAsStateWithLifecycle(viewModel.cached(CURRENT_PLACE_ID))
                CityCard(
                    name = tr("Mi ubicación", "My location"),
                    subtitle = if (hasLocation) here?.place else tr("Sin permiso de ubicación", "No location permission"),
                    snapshot = here,
                    icon = Icons.Rounded.LocationOn,
                    modifier = Modifier.padding(bottom = 10.dp),
                    onClick = if (here != null) ({ onOpenPlace(CURRENT_PLACE_ID) }) else null,
                    action = if (hasLocation) null else {
                        {
                            FilledTonalButton(onClick = {
                                LocationAsk.request(context, locationLauncher)
                            }) { Text(tr("Permitir", "Allow")) }
                        }
                    },
                )
            }

            items(reorder.order, key = { it }) { id ->
                val place = places.firstOrNull { it.id == id } ?: return@items
                val isDragging = reorder.dragging == id
                val snapshot by remember(id) { viewModel.snapshots(id) }
                    .collectAsStateWithLifecycle(viewModel.cached(id))
                val elevation by animateDpAsState(if (isDragging) 12.dp else 0.dp, label = "levantar")
                val dismiss = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.StartToEnd) {
                            // Deslizar hacia la derecha pone un apodo; la tarjeta vuelve a su lugar.
                            renamingId = id
                            false
                        } else if (value != SwipeToDismissBoxValue.Settled) {
                            removeWithUndo(id)
                            true
                        } else {
                            true
                        }
                    },
                )
                // Al deshacer vuelve la misma ciudad (misma clave) y la lista le devuelve su estado
                // guardado "deslizada": se la pone otra vez en su lugar.
                LaunchedEffect(Unit) {
                    if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) dismiss.snapTo(SwipeToDismissBoxValue.Settled)
                }
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
                    modifier = itemModifier.padding(bottom = 10.dp),
                    backgroundContent = {
                        // Solo mientras la tarjeta se corre: quieta, no tiene que haber nada atrás.
                        if (runCatching { dismiss.requireOffset() }.getOrDefault(0f) == 0f) return@SwipeToDismissBox
                        val toStart = dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (toStart) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 24.dp),
                            contentAlignment = if (toStart) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            Icon(
                                if (toStart) Icons.Rounded.Delete else Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = if (toStart) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    },
                ) {
                    CityCard(
                        name = place.shownName,
                        // La hora del lugar, como en el Clima de Apple; si no hay datos, la provincia o país.
                        // Con apodo, también el nombre de verdad.
                        subtitle = listOfNotNull(
                            place.name.takeIf { place.nickname != null },
                            snapshot?.let { shortHour(it.nowAtPlace()) } ?: place.detail,
                        ).joinToString(" · ").ifEmpty { null },
                        snapshot = snapshot,
                        onClick = { onOpenPlace(id) },
                        modifier = Modifier
                            .shadow(elevation, RoundedCornerShape(22.dp))
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
                                    CustomAccessibilityAction(tr("Cambiar nombre", "Rename")) { renamingId = id; true },
                                    CustomAccessibilityAction(tr("Quitar", "Remove")) { removeWithUndo(id); true },
                                )
                            },
                    )
                }
            }

            item {
                Hint(
                    tr(
                        "Tocá una ciudad para ver su pronóstico. Deslizala a la izquierda para quitarla o a la derecha para ponerle un apodo; mantenela apretada para moverla.",
                        "Tap a city to see its forecast. Swipe left to remove it or right to give it a nickname; press and hold to move it.",
                    ),
                )
            }
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

/** Apodo para una ciudad guardada: "Casa", "Trabajo", "Lo de mamá". */
@Composable
private fun RenameDialog(place: Place, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var text by rememberSaveable(place.id) { mutableStateOf(place.nickname.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Apodo para ${place.name}", "Nickname for ${place.name}")) },
        text = {
            Column {
                Text(
                    tr("Se ve en el pronóstico, los widgets y los avisos.", "Shown in the forecast, widgets and alerts."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(24) },
                    placeholder = { Text(tr("Casa, Trabajo…", "Home, Work…")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(tr("Guardar", "Save")) } },
        dismissButton = {
            Row {
                if (place.nickname != null) {
                    TextButton(onClick = { onSave(null) }) { Text(tr("Sacar apodo", "Remove nickname")) }
                }
                TextButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) }
            }
        },
    )
}
