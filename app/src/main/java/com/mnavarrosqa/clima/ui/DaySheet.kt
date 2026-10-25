package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.Day
import com.mnavarrosqa.clima.data.Moon
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.dayLabel
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.longDate
import com.mnavarrosqa.clima.data.timeOf
import com.mnavarrosqa.clima.data.windText
import java.util.Locale
import kotlin.math.roundToInt

/** Detalle de un día: curva por hora y datos del día. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(snapshot: WeatherSnapshot, day: Day, onDismiss: () -> Unit) {
    val today = snapshot.nowAtPlace().toLocalDate()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(dayLabel(day.localDate, today), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        longDate(day.localDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(WeatherCodes.description(day.code), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Máx", "High") + " ${day.max.degrees()} · " + tr("Mín", "Low") + " ${day.min.degrees()}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                AnimatedWeatherIcon(day.code, isDay = true, size = 88.dp)
            }

            val hours = snapshot.hoursOf(day.localDate)
            if (hours.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    tr("Por hora", "Hourly"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
                HourlyChart(hours, Modifier.padding(horizontal = 8.dp), alerts = remember(snapshot) { alertSpans(snapshot) })
            }

            Spacer(Modifier.height(16.dp))
            val facts = buildList {
                add(tr("Probabilidad de lluvia", "Chance of rain") to "${day.precipitationProbability ?: 0}%")
                day.precipitationSum?.let { add(tr("Lluvia total", "Total rain") to String.format(Locale.US, "%.1f mm", it)) }
                day.windMax?.let { add(tr("Viento máximo", "Max wind") to windText(it)) }
                day.uvIndex?.let { add(tr("Índice UV máximo", "Max UV index") to "${it.roundToInt()} · ${WeatherCodes.uvLabel(it)}") }
                add(tr("Amanecer", "Sunrise") to timeOf(day.sunrise))
                add(tr("Atardecer", "Sunset") to timeOf(day.sunset))
                add(tr("Luna", "Moon") to Moon.phaseName(day.localDate))
            }
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                facts.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (label, value) ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(value, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

