package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import android.content.Context
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.mnavarrosqa.clima.data.shortHour
import androidx.compose.material3.Scaffold
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitiesScreen(
    viewModel: WeatherViewModel,
    onLocationPermission: (Boolean) -> Unit,
    onOpenPlace: (String) -> Unit = {},
    // Comparar ciudades: hace falta más de un lugar ([canCompare]).
    canCompare: Boolean = false,
    onCompare: () -> Unit = {},
    // Al llegar desde "Elegir una ciudad" el buscador toma el foco; [onSearchFocused] avisa que ya se hizo.
    focusSearch: Boolean = false,
    onSearchFocused: () -> Unit = {},
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
    val searchFocus = remember { FocusRequester() }
    /** Sube al buscador y le da el foco (abre el teclado). */
    suspend fun focusSearchField() {
        listState.scrollToItem(0)
        // Un cuadro para que el campo esté en la lista antes de pedirle el foco.
        withFrameNanos { }
        runCatching { searchFocus.requestFocus() }
    }
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            focusSearchField()
            onSearchFocused()
        }
    }
    val reorder = remember { CityReorder(listState).apply { order = places.map { it.id } } }
    // Mientras no se arrastra, el orden es el guardado.
    LaunchedEffect(places) { if (reorder.dragging == null) reorder.order = places.map { it.id } }
    val finishDrag: () -> Unit = {
        reorder.end()?.let { (id, moved) -> viewModel.movePlace(id, moved) }
    }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val density = LocalDensity.current
    // Arrastrando cerca del borde de arriba o de abajo, la lista se corre sola.
    LaunchedEffect(reorder.dragging) {
        if (reorder.dragging == null) return@LaunchedEffect
        val threshold = with(density) { 72.dp.toPx() }
        val maxStep = with(density) { 14.dp.toPx() }
        while (true) {
            withFrameNanos { }
            val step = reorder.edgeScroll(threshold, maxStep)
            if (step != 0f) {
                val moved = listState.scrollBy(step)
                if (moved != 0f && reorder.scrolled(moved)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }
    // Ciudad recién agregada desde el buscador: se avisa y se baja hasta ella cuando aparece.
    var justAdded by remember { mutableStateOf<Place?>(null) }
    LaunchedEffect(justAdded) {
        val place = justAdded ?: return@LaunchedEffect
        // En el scope de la pantalla: este efecto se cancela al terminar y cerraría el aviso.
        scope.launch { snackbar.showSnackbar(tr("Agregaste ${place.shownName}", "Added ${place.shownName}"), duration = SnackbarDuration.Short) }
        // Va al final de la lista, justo antes de la ayuda; se espera a que esté y a que la lista la mida.
        snapshotFlow { reorder.order.lastOrNull() == place.id && query.isBlank() }.first { it }
        withFrameNanos { }
        listState.animateScrollToItem((listState.layoutInfo.totalItemsCount - 2).coerceAtLeast(0))
        justAdded = null
    }
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
            } else {
                viewModel.discardForecast(id)
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

    var gesturesHintSeen by remember {
        mutableStateOf(context.getSharedPreferences("clima", Context.MODE_PRIVATE).getBoolean(GESTURES_HINT, false))
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Arriba de la barra flotante: debajo, "Deshacer" no se veía ni se podía tocar.
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomBarSpace.current)) },
        topBar = {
            // Mediana: la grande dejaba un hueco vacío arriba y empujaba las ciudades hacia abajo.
            MediumTopAppBar(
                title = { Text(tr("Ciudades", "Cities")) },
                // Sin flecha de volver: es una pestaña de la barra, no una pantalla abierta encima.
                actions = {
                    IconButton(onClick = onCompare, enabled = canCompare) {
                        Icon(CompareIcon, contentDescription = tr("Comparar ciudades", "Compare cities"))
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
                bottom = padding.calculateBottomPadding() + 24.dp + LocalBottomBarSpace.current,
            ),
        ) {
            // Buscador relleno y sin borde, como el de iOS.
            item {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .focusRequester(searchFocus),
                    placeholder = { Text(tr("Buscar una ciudad", "Search for a city")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) null else {
                        {
                            TextButton(onClick = { query = "" }) { Text(tr("Cancelar", "Cancel")) }
                        }
                    },
                    singleLine = true,
                    shape = ClimaShapes.inner,
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
                // Solo cuando llegó la respuesta de lo que está escrito: mientras se tipea no.
                query.trim().length >= 2 && search.query == query.trim() && search.results.isEmpty() ->
                    item { Hint(tr("No encontré esa ciudad", "City not found")) }
                query.trim().length >= 2 && search.query != query.trim() && search.results.isEmpty() ->
                    item { Hint(tr("Buscando…", "Searching…")) }
            }
            // Los resultados van juntos en un grupo redondeado, como las filas de Ajustes.
            itemsIndexed(search.results, key = { _, place -> "r-" + place.id }) { index, place ->
                // Las que ya están guardadas se marcan: agregarlas de nuevo no hacía nada.
                val saved = places.any { it.id == place.id }
                // Por fuera, las esquinas de un grupo (las de ClimaShapes.tile); entre filas, chicas.
                val top = if (index == 0) 24.dp else 4.dp
                val bottom = if (index == search.results.lastIndex) 24.dp else 4.dp
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = place.detail?.let { detail -> @Composable { Text(detail) } },
                    leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    trailingContent = {
                        if (saved) {
                            Text(tr("Ya está", "Added"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(Icons.Rounded.Add, contentDescription = tr("Agregar", "Add"))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
                        .clickable {
                            if (saved) {
                                onOpenPlace(place.id)
                            } else {
                                viewModel.addPlace(place)
                                justAdded = place
                            }
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
                    // Las ciudades guardadas tienen "⋮": así las temperaturas quedan en columna.
                    alignWithMenu = places.isNotEmpty() && hasLocation,
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
                                .clip(ClimaShapes.tile)
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
                        menu = {
                            val index = reorder.order.indexOf(id)
                            CityMenu(
                                name = place.shownName,
                                canMoveUp = index > 0,
                                canMoveDown = index in 0 until reorder.order.lastIndex,
                                onRename = { renamingId = id },
                                onMove = { viewModel.movePlace(id, it) },
                                onRemove = { removeWithUndo(id) },
                            )
                        },
                        modifier = Modifier
                            .shadow(elevation, ClimaShapes.tile)
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
                                    CustomAccessibilityAction(tr("Apodo", "Nickname")) { renamingId = id; true },
                                    CustomAccessibilityAction(tr("Quitar", "Remove")) { removeWithUndo(id); true },
                                )
                            },
                    )
                }
            }

            if (places.isEmpty()) {
                // Sin ciudades guardadas: qué se puede hacer acá, con el buscador a un toque.
                item(key = "vacio") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            tr("Agregá tu primera ciudad", "Add your first city"),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr(
                                "Buscala por nombre para ver su clima, compararla con otras y usarla en los widgets.",
                                "Search by name to see its weather, compare it with others and use it in widgets.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 360.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { scope.launch { focusSearchField() } }) {
                            Text(tr("Buscar una ciudad", "Search for a city"))
                        }
                    }
                }
            } else {
                // Los gestos solo tienen sentido con alguna ciudad a la vista. Una vez leída, se
                // puede cerrar: el "⋮" de cada ciudad hace lo mismo.
                if (!gesturesHintSeen) {
                    item(key = "pista") {
                        GesturesHint(
                            onDismiss = {
                                gesturesHintSeen = true
                                context.getSharedPreferences("clima", Context.MODE_PRIVATE).edit().putBoolean(GESTURES_HINT, true).apply()
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
            }
        }
    }
}

private const val GESTURES_HINT = "pista_ciudades_vista"

/** Cómo quitar, poner apodo y mover, con un botón para no mostrarlo más. */
@Composable
private fun GesturesHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(ClimaShapes.inner)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tr(
                "Deslizá a la izquierda para quitar y a la derecha para ponerle un apodo. Mantené apretada para moverla.",
                "Swipe left to remove and right to nickname. Press and hold to move it.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) { Text(tr("Entendido", "Got it")) }
    }
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

/** El "⋮" de cada ciudad guardada: lo mismo que los gestos, a la vista. */
@Composable
private fun CityMenu(
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRename: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = tr("Opciones de $name", "Options for $name"), tint = SkySurface.content)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(tr("Apodo", "Nickname")) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { open = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(tr("Subir", "Move up")) },
                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null) },
                enabled = canMoveUp,
                onClick = { open = false; onMove(-1) },
            )
            DropdownMenuItem(
                text = { Text(tr("Bajar", "Move down")) },
                leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
                enabled = canMoveDown,
                onClick = { open = false; onMove(1) },
            )
            DropdownMenuItem(
                text = { Text(tr("Quitar", "Remove")) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = { open = false; onRemove() },
            )
        }
    }
}

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
