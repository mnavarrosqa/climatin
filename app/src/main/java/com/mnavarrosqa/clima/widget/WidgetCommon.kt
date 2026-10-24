package com.mnavarrosqa.clima.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.ui.theme.DarkColors
import com.mnavarrosqa.clima.ui.theme.LightColors

/** En Android 12+ GlanceTheme toma los colores del fondo de pantalla (Material You). */
@Composable
fun ClimaGlanceTheme(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = ColorProviders(light = LightColors, dark = DarkColors), content = content)
    }
}

/** Contenedor redondeado del widget; tocarlo abre la app. */
@Composable
fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun EmptyWidget() {
    WidgetSurface(modifier = GlanceModifier.padding(16.dp)) {
        Text(
            "Abrí Climatin para ver el pronóstico",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Ciudad elegida para cada widget, guardada en el estado de Glance de esa instancia. */
object WidgetPlace {
    val KEY = stringPreferencesKey("place")
}

/** Pronóstico de la ciudad de este widget; se recompone cuando cambia la caché. */
@Composable
fun widgetSnapshot(): WeatherSnapshot? {
    val repository = WeatherRepository.get(LocalContext.current)
    val placeId = repository.resolvePlaceId(currentState(WidgetPlace.KEY))
    val snapshot by remember(placeId) { repository.snapshots(placeId) }
        .collectAsState(repository.cached(placeId))
    return snapshot
}

suspend fun updateWeatherWidgets(context: Context) {
    runCatching { ClimaWidget().updateAll(context) }
    runCatching { PronosticoWidget().updateAll(context) }
    runCatching { VistazoWidget().updateAll(context) }
    runCatching { GraficoWidget().updateAll(context) }
}
