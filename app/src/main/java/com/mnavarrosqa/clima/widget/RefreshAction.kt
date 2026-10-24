package com.mnavarrosqa.clima.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot

/** Tocar ⟳ en un widget refresca el clima sin abrir la app y muestra "Actualizando…" mientras tanto. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[WidgetPrefs.REFRESHING] = true }
        updateWeatherWidgets(context)
        try {
            WeatherRepository.get(context).refreshAll(force = true)
        } finally {
            updateAppWidgetState(context, glanceId) { it[WidgetPrefs.REFRESHING] = false }
            updateWeatherWidgets(context)
        }
    }
}

/** Tocar › pasa el widget a la siguiente ciudad: donde estoy, después las guardadas, y vuelve. */
class NextCityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repository = WeatherRepository.get(context)
        val ids = listOf(CURRENT_PLACE_ID) + repository.places().map { it.id }
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = repository.resolvePlaceId(prefs[WidgetPrefs.PLACE])
            prefs[WidgetPrefs.PLACE] = ids[(ids.indexOf(current) + 1) % ids.size]
        }
        updateWeatherWidgets(context)
    }
}

/** Nombre de la ciudad con ⟳ para actualizar y, si hay varias ciudades, › para pasar a la siguiente. */
@Composable
fun PlaceLabel(
    snapshot: WeatherSnapshot,
    colors: WidgetColors,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val refreshing = currentState(WidgetPrefs.REFRESHING) == true
    val hasOtherCities = WeatherRepository.get(LocalContext.current).places().isNotEmpty()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (refreshing) "Actualizando…" else snapshot.place ?: "Tu ubicación",
            maxLines = 1,
            style = TextStyle(color = colors.textVariant, fontSize = fontSize, fontWeight = fontWeight),
        )
        Spacer(GlanceModifier.width(2.dp))
        if (!refreshing) {
            SmallButton(R.drawable.ic_refresh, "Actualizar", colors) { actionRunCallback<RefreshAction>() }
            if (hasOtherCities) {
                SmallButton(R.drawable.ic_chevron_right, "Siguiente ciudad", colors) { actionRunCallback<NextCityAction>() }
            }
        }
    }
}

@Composable
private fun SmallButton(
    icon: Int,
    description: String,
    colors: WidgetColors,
    action: () -> androidx.glance.action.Action,
) {
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        colorFilter = ColorFilter.tint(colors.textVariant),
        modifier = GlanceModifier
            .size(24.dp)
            .padding(4.dp)
            .clickable(action()),
    )
}
