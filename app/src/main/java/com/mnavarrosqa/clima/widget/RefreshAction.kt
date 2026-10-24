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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot

/** Tocar el ícono de actualizar en un widget refresca el clima sin abrir la app. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WeatherRepository.get(context).refreshAll()
        updateWeatherWidgets(context)
    }
}

/** Nombre de la ciudad con el botón de actualizar al lado. */
@Composable
fun PlaceLabel(
    snapshot: WeatherSnapshot,
    colors: WidgetColors,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            snapshot.place ?: "Tu ubicación",
            maxLines = 1,
            style = TextStyle(color = colors.textVariant, fontSize = fontSize, fontWeight = fontWeight),
        )
        Spacer(GlanceModifier.width(2.dp))
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Actualizar",
            colorFilter = ColorFilter.tint(colors.textVariant),
            modifier = GlanceModifier
                .size(22.dp)
                .padding(4.dp)
                .clickable(actionRunCallback<RefreshAction>()),
        )
    }
}
