package com.mnavarrosqa.clima.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mnavarrosqa.clima.data.LocationProvider
import com.mnavarrosqa.clima.data.PressureUnit
import com.mnavarrosqa.clima.data.TemperatureUnit
import com.mnavarrosqa.clima.data.WindUnit
import com.mnavarrosqa.clima.work.RainNotifier
import kotlinx.coroutines.delay

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
    val rainAlerts by viewModel.rainAlerts.collectAsStateWithLifecycle()
    val units by viewModel.units.collectAsStateWithLifecycle()
    val smnAlerts by viewModel.smnAlerts.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var hasLocation by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    var hasBackground by remember { mutableStateOf(LocationProvider.hasBackgroundPermission(context)) }
    var canNotify by remember { mutableStateOf(RainNotifier.canNotify(context)) }

    // Los permisos se pueden cambiar desde Ajustes del sistema: se releen al volver.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocation = LocationProvider.hasPermission(context)
        hasBackground = LocationProvider.hasBackgroundPermission(context)
        canNotify = RainNotifier.canNotify(context)
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasLocation = it
        onLocationPermission(it)
    }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasBackground = it
    }
    // Qué aviso pidió el permiso de notificaciones, para activarlo cuando lo den.
    var pendingNotification by remember { mutableStateOf<String?>(null) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        canNotify = it
        when (pendingNotification) {
            "smn" -> viewModel.setSmnAlerts(it)
            else -> viewModel.setRainAlerts(it)
        }
        pendingNotification = null
    }

    LaunchedEffect(query) {
        delay(350)
        viewModel.searchCities(query)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Ciudades y ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                    placeholder = { Text("Buscar una ciudad") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }
            when {
                search.loading -> item { Hint("Buscando…") }
                search.error != null -> item { Hint(search.error.orEmpty()) }
                query.trim().length >= 2 && search.results.isEmpty() -> item { Hint("No encontré esa ciudad") }
            }
            items(search.results, key = { "r-" + it.id }) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = place.detail?.let { detail -> @Composable { Text(detail) } },
                    leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    trailingContent = { Icon(Icons.Rounded.Add, contentDescription = "Agregar") },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable {
                        viewModel.addPlace(place)
                        query = ""
                    },
                )
            }

            item { SectionTitle("Tus ciudades") }
            item {
                Group {
                    ListItem(
                        headlineContent = { Text("Donde estoy") },
                        supportingContent = {
                            Text(if (hasLocation) "Según tu ubicación" else "Sin permiso de ubicación")
                        },
                        leadingContent = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                        trailingContent = if (hasLocation) null else {
                            {
                                FilledTonalButton(onClick = {
                                    locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }) { Text("Permitir") }
                            }
                        },
                        colors = groupColors(),
                    )
                }
            }
            items(places, key = { it.id }) { place ->
                val index = places.indexOf(place)
                Group(Modifier.padding(top = 2.dp)) {
                    ListItem(
                        headlineContent = { Text(place.name) },
                        supportingContent = place.detail?.let { detail -> @Composable { Text(detail) } },
                        leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.movePlace(place.id, -1) }, enabled = index > 0) {
                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Subir")
                                }
                                IconButton(
                                    onClick = { viewModel.movePlace(place.id, 1) },
                                    enabled = index < places.lastIndex,
                                ) {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Bajar")
                                }
                                IconButton(onClick = { viewModel.removePlace(place.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Quitar")
                                }
                            }
                        },
                        colors = groupColors(),
                    )
                }
            }

            item { SectionTitle("Ajustes") }
            item {
                Group {
                    ListItem(
                        headlineContent = { Text("Ubicación con la app cerrada") },
                        supportingContent = {
                            Text(
                                if (hasBackground && hasLocation) {
                                    "Los widgets siguen tu ubicación aunque no abras la app"
                                } else {
                                    "Elegí \"Permitir todo el tiempo\" para que los widgets te sigan"
                                },
                            )
                        },
                        trailingContent = if (hasBackground && hasLocation) null else {
                            {
                                FilledTonalButton(onClick = {
                                    if (!hasLocation) {
                                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    }
                                }) { Text("Permitir") }
                            }
                        },
                        colors = groupColors(),
                    )
                }
            }
            item {
                Group(Modifier.padding(top = 2.dp)) {
                    ListItem(
                        headlineContent = { Text("Avisos de lluvia") },
                        supportingContent = { Text("Una notificación cuando va a llover donde estás") },
                        trailingContent = {
                            Switch(
                                checked = rainAlerts && canNotify,
                                onCheckedChange = { enabled ->
                                    if (enabled && !canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        pendingNotification = "lluvia"
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.setRainAlerts(enabled)
                                    }
                                },
                            )
                        },
                        colors = groupColors(),
                    )
                }
            }
            item {
                Group(Modifier.padding(top = 2.dp)) {
                    ListItem(
                        headlineContent = { Text("Alertas del SMN") },
                        supportingContent = { Text("Tormentas, viento, calor y otras alertas oficiales en Argentina") },
                        trailingContent = {
                            Switch(
                                checked = smnAlerts && canNotify,
                                onCheckedChange = { enabled ->
                                    if (enabled && !canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        pendingNotification = "smn"
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.setSmnAlerts(enabled)
                                    }
                                },
                            )
                        },
                        colors = groupColors(),
                    )
                }
            }
            item { SectionTitle("Unidades") }
            item {
                Group {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        ChoiceRow("Temperatura", TemperatureUnit.entries, units.temperature, { it.label }) {
                            viewModel.setUnits(units.copy(temperature = it))
                        }
                        ChoiceRow("Viento", WindUnit.entries, units.wind, { it.label }) {
                            viewModel.setUnits(units.copy(wind = it))
                        }
                        ChoiceRow("Presión", PressureUnit.entries, units.pressure, { it.label }) {
                            viewModel.setUnits(units.copy(pressure = it))
                        }
                        ListItem(
                            headlineContent = { Text("Formato de 24 horas") },
                            trailingContent = {
                                Switch(
                                    checked = units.use24h,
                                    onCheckedChange = { viewModel.setUnits(units.copy(use24h = it)) },
                                )
                            },
                            colors = groupColors(),
                        )
                    }
                }
            }
            item {
                Hint("Los widgets se configuran al agregarlos: mantené apretado uno y tocá el lápiz para cambiar la ciudad.")
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(labelOf(option)) },
                )
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
