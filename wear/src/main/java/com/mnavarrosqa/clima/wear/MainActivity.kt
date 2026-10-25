package com.mnavarrosqa.clima.wear

import android.Manifest
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.mnavarrosqa.clima.data.Day
import com.mnavarrosqa.clima.data.Hour
import com.mnavarrosqa.clima.data.UnitSettings
import com.mnavarrosqa.clima.data.Units
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.ageText
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.rainOutlook
import com.mnavarrosqa.clima.data.rainOutlookText
import com.mnavarrosqa.clima.data.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Pantalla del reloj: el clima de ahora, las próximas horas y la semana, en una lista que gira con la corona. */
class MainActivity : ComponentActivity() {

    private var snapshot by mutableStateOf<WeatherSnapshot?>(null)

    // Las unidades viven en Units.current (fuera de Compose): con esto la pantalla se redibuja al cambiarlas.
    private var units by mutableStateOf(Units.current)
    private var refreshing by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && WearStore.cached(this) == null) {
            message = tr("Sin permiso de ubicación: abrí Climatin en el teléfono o permitila en Ajustes", "No location permission: open Climatin on your phone or allow it in Settings")
        } else {
            refresh()
        }
    }

    // Si el teléfono manda datos con la app abierta, se ven al instante. Campo (y no lambda
    // suelta) porque SharedPreferences guarda el listener con referencia débil.
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        WearStore.loadUnits(this)
        units = Units.current
        WearStore.cached(this)?.let { snapshot = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        snapshot = WearStore.cached(this)
        setContent {
            WeatherScreen(
                snapshot = snapshot,
                units = units,
                refreshing = refreshing,
                message = message,
                onRefresh = ::manualRefresh,
            )
        }
        // Con los datos del teléfono no hace falta pedir la ubicación del reloj.
        if (!WearStore.hasPermission(this) && !WearStore.phoneIsFresh(this)) permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    override fun onStart() {
        super.onStart()
        WearStore.listen(this, changes)
    }

    override fun onStop() {
        WearStore.stopListening(this, changes)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Al volver a la app, si los datos tienen más de 15 minutos, se actualizan.
        val cached = WearStore.cached(this)
        snapshot = cached
        units = Units.current
        if (cached == null || System.currentTimeMillis() - cached.fetchedAt > 15 * 60_000L) refresh()
    }

    /** El chip "Actualizar": baja el clima aunque el teléfono haya mandado hace poco. */
    private fun manualRefresh() {
        if (!WearStore.hasPermission(this) && WearStore.cached(this) == null) {
            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            refresh(force = true)
        }
    }

    private fun refresh(force: Boolean = false) {
        // El permiso y onResume pueden pedirlo a la vez: con una descarga alcanza.
        if (refreshing) return
        refreshing = true
        message = null
        lifecycleScope.launch {
            val result = WearStore.refresh(this@MainActivity, force)
            snapshot = result.getOrNull() ?: WearStore.cached(this@MainActivity)
            refreshing = false
            if (result.isFailure) {
                message = if (snapshot == null) tr("No pude obtener el clima", "Couldn't get the weather")
                else tr("No pude actualizar", "Couldn't update")
            }
        }
    }
}

@Composable
private fun WeatherScreen(
    snapshot: WeatherSnapshot?,
    units: UnitSettings,
    refreshing: Boolean,
    message: String?,
    onRefresh: () -> Unit,
) {
    MaterialTheme {
        val listState = rememberScalingLazyListState()
        val focusRequester = remember { FocusRequester() }
        // Para "Actualizado hace X": sin esto el texto queda congelado mientras la app está abierta.
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                now = System.currentTimeMillis()
            }
        }
        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        ) {
            // key: los textos se arman con Units.current; al cambiar las unidades se rehacen.
            key(units) {
                ScalingLazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .rotaryScrollable(RotaryScrollableDefaults.behavior(scrollableState = listState), focusRequester),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // La corona la maneja el modificador de arriba, con su FocusRequester.
                    rotaryScrollableBehavior = null,
                ) {
                    if (snapshot == null) {
                        // Todavía no llegó nada del teléfono ni se pudo bajar: qué pasa y cómo reintentar.
                        item { Text("Climatin", style = MaterialTheme.typography.title2) }
                        item {
                            Text(
                                message ?: tr("Buscando el clima…", "Getting the weather…"),
                                style = MaterialTheme.typography.body2,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurfaceVariant,
                            )
                        }
                        item { RefreshChip(refreshing, onRefresh) }
                    } else {
                        weatherItems(snapshot.withCurrentFromHours(now), refreshing, message, now, onRefresh)
                    }
                }
            }
        }
        // Sin foco la corona no mueve la lista; el key de arriba lo pierde al cambiar las unidades.
        LaunchedEffect(units) { focusRequester.requestFocus() }
    }
}

private fun ScalingLazyListScope.weatherItems(
    snapshot: WeatherSnapshot,
    refreshing: Boolean,
    message: String?,
    now: Long,
    onRefresh: () -> Unit,
) {
    val current = snapshot.current
    val today = snapshot.today()
    val todayDate = snapshot.nowAtPlace().toLocalDate()
    val rain = snapshot.rainOutlook(withinMinutes = 120)
    val hours = snapshot.upcomingHours(9).drop(1)
    val days = snapshot.upcomingDays(7)

    item {
        Text(
            snapshot.place ?: tr("Ubicación actual", "Current location"),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Image(
                painter = painterResource(WeatherCodes.icon(current.code, current.isDay)),
                contentDescription = WeatherCodes.description(current.code),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(current.temperature.degrees(), style = MaterialTheme.typography.display1)
        }
    }
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                WeatherCodes.description(current.code),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
            )
            if (today != null) {
                Text(
                    tr("Máx", "High") + " ${today.max.degrees()} · " + tr("Mín", "Low") + " ${today.min.degrees()}",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
    // Lluvia que empieza o para en las próximas dos horas, como el aviso del teléfono.
    if (rain != null) {
        item {
            Text(
                rainOutlookText(rain),
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (hours.isNotEmpty()) {
        item { ListHeader { Text(tr("Próximas horas", "Next hours")) } }
        // De a cuatro por fila: deslizar de costado en el reloj choca con el gesto de volver.
        hours.chunked(4).forEach { row ->
            item { HoursRow(row) }
        }
    }
    if (days.isNotEmpty()) {
        item { ListHeader { Text(tr("Próximos días", "Next days")) } }
        items(days, key = { it.date }) { day -> DayRow(day, todayDate) }
    }
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
            RefreshChip(refreshing, onRefresh)
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    item {
        Text(
            tr("Actualizado ", "Updated ") + ageText(snapshot.fetchedAt, now),
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RefreshChip(refreshing: Boolean, onRefresh: () -> Unit) {
    CompactChip(
        onClick = onRefresh,
        enabled = !refreshing,
        colors = ChipDefaults.secondaryChipColors(),
        label = { Text(if (refreshing) tr("Actualizando…", "Updating…") else tr("Actualizar", "Refresh")) },
    )
}

@Composable
private fun HoursRow(hours: List<Hour>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        hours.forEach { hour ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {},
            ) {
                Text(
                    compactHour(hour.dateTime),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.onSurfaceVariant,
                    maxLines = 1,
                )
                Image(
                    painter = painterResource(WeatherCodes.icon(hour.code, hour.isDay)),
                    contentDescription = WeatherCodes.description(hour.code),
                    modifier = Modifier.size(24.dp),
                )
                Text(hour.temperature.degrees(), style = MaterialTheme.typography.caption1, maxLines = 1)
                // Siempre ocupa su lugar, para que las columnas queden alineadas.
                Text(
                    rainChance(hour.precipitationProbability),
                    style = MaterialTheme.typography.caption3,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                )
            }
        }
        // Fila incompleta: celdas vacías para que no se estiren las que hay.
        repeat(4 - hours.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun DayRow(day: Day, today: LocalDate) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            dayLabel(day.localDate, today),
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Image(
            painter = painterResource(WeatherCodes.icon(day.code)),
            contentDescription = WeatherCodes.description(day.code),
            modifier = Modifier.size(22.dp),
        )
        Text(
            rainChance(day.precipitationProbability),
            style = MaterialTheme.typography.caption3,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(30.dp),
        )
        Text(
            day.max.degrees(),
            style = MaterialTheme.typography.caption1,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(30.dp),
        )
        Text(
            day.min.degrees(),
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(30.dp),
        )
    }
}

/** "40%" si la probabilidad de lluvia vale la pena; con menos de 10% no se muestra. */
private fun rainChance(probability: Int?): String =
    probability?.takeIf { it >= 10 }?.let { "$it%" } ?: ""
