package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import com.mnavarrosqa.clima.data.NoLocationException
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.work.afterRefresh
import com.mnavarrosqa.clima.data.WeatherSnapshot

/** Tocar ⟳ en un widget refresca el clima sin abrir la app y muestra "Actualizando…" mientras tanto. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { it[WidgetPrefs.REFRESHING_AT] = System.currentTimeMillis() }
        // Por si Android cierra el proceso a mitad de camino: un repintado en 2 minutos saca
        // "Actualizando…" (el cartel vence a los 90 s, pero nadie volvería a dibujar el widget).
        WorkManager.getInstance(context).enqueueUniqueWork(
            "widgets-repintar",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetRedrawWorker>().setInitialDelay(2, TimeUnit.MINUTES).build(),
        )
        // Solo este widget, para mostrar "Actualizando…"; al final se actualizan todos.
        updateOne(context, glanceId)
        var failed = false
        try {
            failed = WeatherRepository.get(context).refreshAll(force = true).let { it != null && it !is NoLocationException }
        } finally {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs.remove(WidgetPrefs.REFRESHING_AT)
                // Si no se pudo, el widget lo dice un rato en vez de volver como si nada.
                if (failed) prefs[WidgetPrefs.REFRESH_FAILED_AT] = System.currentTimeMillis() else prefs.remove(WidgetPrefs.REFRESH_FAILED_AT)
            }
            // Igual que al actualizar desde la app: también los avisos, la temperatura en la
            // barra de estado y el reloj.
            afterRefresh(context, urgentWear = true)
        }
    }
}

/** Tocar › pasa el widget a la siguiente ciudad: donde estoy, después las guardadas, y vuelve. */
class NextCityAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repository = WeatherRepository.get(context)
        val ids = listOf(CURRENT_PLACE_ID) + repository.places().map { it.id }
        updateAppWidgetState(context, glanceId) { prefs ->
            // Un widget que rota sigue rotando: › no tiene que apagar la rotación.
            if (prefs[WidgetPrefs.PLACE] == ROTATE_PLACE_ID) return@updateAppWidgetState
            val current = resolveWidgetPlace(context, prefs[WidgetPrefs.PLACE])
            prefs[WidgetPrefs.PLACE] = ids[(ids.indexOf(current) + 1) % ids.size]
        }
        // Cambió un solo widget: no hace falta actualizar los once tipos.
        updateOne(context, glanceId)
    }
}

/** Vuelve a dibujar los widgets con lo guardado, sin descargar nada. */
class WidgetRedrawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { updateWeatherWidgets(applicationContext) }
        return Result.success()
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
    val now = System.currentTimeMillis()
    val refreshing = (currentState(WidgetPrefs.REFRESHING_AT) ?: 0L) > now - 90_000
    val failed = (currentState(WidgetPrefs.REFRESH_FAILED_AT) ?: 0L) > now - 10 * 60_000
    // Si el pronóstico tiene horas (el sistema demoró las actualizaciones o no hubo red), se dice:
    // si no, el widget parecía al día con datos viejos.
    val age = now - snapshot.fetchedAt
    val staleNote = when {
        failed -> tr("Sin conexión", "Offline")
        age > 2 * 60 * 60_000L -> tr("hace ${age / (60 * 60_000L)} h", "${age / (60 * 60_000L)} h ago")
        else -> null
    }
    val hasOtherCities = WeatherRepository.get(LocalContext.current).places().isNotEmpty()
    val showPlace = widgetShows(WidgetPart.PLACE)
    val showRefresh = widgetShows(WidgetPart.REFRESH)
    // En un widget que rota, › no tiene sentido: la ciudad cambia sola.
    val showNext = widgetShows(WidgetPart.NEXT) && currentState(WidgetPrefs.PLACE) != ROTATE_PLACE_ID
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (refreshing || showPlace) {
            Text(
                if (refreshing) tr("Actualizando…", "Updating…") else snapshot.place ?: tr("Mi ubicación", "My location"),
                maxLines = 1,
                style = TextStyle(color = colors.textVariant, fontSize = fontSize, fontWeight = fontWeight),
            )
            Spacer(GlanceModifier.width(2.dp))
        }
        if (!refreshing && staleNote != null && (showPlace || showRefresh)) {
            Text(
                (if (showPlace) "· " else "") + staleNote,
                maxLines = 1,
                style = TextStyle(color = colors.textVariant, fontSize = fontSize),
            )
            Spacer(GlanceModifier.width(2.dp))
        }
        if (!refreshing) {
            if (showRefresh) {
                SmallButton(R.drawable.ic_refresh, tr("Actualizar", "Refresh"), colors) { actionRunCallback<RefreshAction>() }
            }
            if (hasOtherCities && showNext) {
                SmallButton(R.drawable.ic_chevron_right, tr("Siguiente ciudad", "Next city"), colors) { actionRunCallback<NextCityAction>() }
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
            // Área de toque más grande que el ícono, para que sea fácil acertarle.
            .size(32.dp)
            .padding(8.dp)
            .clickable(action()),
    )
}

/** Redibuja solo el widget tocado. */
private suspend fun updateOne(context: Context, glanceId: GlanceId) {
    runCatching {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val kind = WidgetCatalog.kindOf(context, id) ?: return@runCatching updateWeatherWidgets(context)
        (kind.widget() as GlanceAppWidget).update(context, glanceId)
    }
}
