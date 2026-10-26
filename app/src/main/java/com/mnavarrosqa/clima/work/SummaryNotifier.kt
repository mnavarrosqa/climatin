package com.mnavarrosqa.clima.work

import com.mnavarrosqa.clima.data.tr
import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.windText
import com.mnavarrosqa.clima.work.Notify.muteAction
import com.mnavarrosqa.clima.work.Notify.radarAction
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Resumen diario a la hora elegida: máxima y mínima, cuándo llueve, UV y viento. Desde las 17
 * resume el día siguiente ("Mañana en Córdoba…"). Es de donde estás o de una ciudad guardada.
 */
object SummaryNotifier {
    private const val WORK = "resumen-diario"

    /** Agenda el próximo resumen (o lo cancela si está apagado). */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val settings = NotifyPrefs.get(context)
        val work = WorkManager.getInstance(context)
        if (!settings.summary) {
            work.cancelUniqueWork(WORK)
            return
        }
        // Con zona: entre dos LocalDateTime la noche del cambio de hora salía una hora corrida.
        val now = ZonedDateTime.now()
        var next = now.toLocalDate().atTime(settings.summaryHour, 0).atZone(now.zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(settings.summaryHour, 0).atZone(now.zone)
        val request = OneTimeWorkRequestBuilder<SummaryWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(WORK, policy, request)
    }

    /**
     * Agenda el próximo desde el trabajo que está corriendo, encadenado detrás. Si el sistema cortó
     * y volvió a correr el trabajo, el siguiente ya quedó colgado: sin este control la cadena crecía.
     */
    fun scheduleAfter(context: Context, running: java.util.UUID) {
        val infos = runCatching { WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK).get() }.getOrNull()
        if (infos != null && infos.any { it.id != running && !it.state.isFinished }) return
        schedule(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /**
     * El lugar elegido para el resumen. Si la ciudad se borró, vuelve a "donde estoy" (sin tocar
     * el ajuste: si la vuelve a agregar con el mismo id, sigue elegida).
     */
    fun placeId(context: Context): String {
        val chosen = NotifyPrefs.get(context).summaryPlace
        if (chosen == CURRENT_PLACE_ID) return chosen
        return if (WeatherRepository.get(context).places().any { it.id == chosen }) chosen else CURRENT_PLACE_ID
    }

    fun notify(context: Context, test: Boolean = false) {
        val repository = WeatherRepository.get(context)
        val chosen = placeId(context)
        // Sin pronóstico de la ciudad (recién agregada, sin conexión), mejor el de donde estás que nada.
        val cityForecast = repository.cached(chosen)
        val placeId = if (cityForecast != null) chosen else CURRENT_PLACE_ID
        val snapshot = cityForecast ?: repository.cached() ?: return
        // El resumen sale a la hora que eligió la persona aunque caiga en horario de silencio
        // (si no, con resumen a las 7 y silencio hasta las 8 no saldría nunca); solo lo frena la pausa.
        if (!test && (NotifyPrefs.isMuted(context) || !RainNotifier.canNotify(context))) return
        show(context, snapshot, placeId)
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, snapshot: WeatherSnapshot, placeId: String) {
        if (!RainNotifier.canNotify(context)) return
        val now = snapshot.nowAtPlace()
        val tomorrow = now.hour >= 17
        val days = snapshot.upcomingDays(2)
        val day = (if (tomorrow) days.getOrNull(1) else days.firstOrNull()) ?: return
        val place = snapshot.place ?: tr("donde estás", "where you are")
        val title = (if (tomorrow) tr("Mañana en", "Tomorrow in") else tr("Hoy en", "Today in")) +
            " $place: ${day.min.degrees()} " + tr("a", "to") + " ${day.max.degrees()}"

        val hours = snapshot.hoursOf(day.localDate)
        val rainyHour = hours.firstOrNull { (it.precipitationProbability ?: 0) >= 50 }
        val rain = when {
            rainyHour != null -> tr("Lluvia probable desde las", "Rain likely from") + " ${shortHour(rainyHour.dateTime)} (${rainyHour.precipitationProbability}%)"
            (day.precipitationProbability ?: 0) >= 30 -> tr("Chance de lluvia", "Chance of rain") + " ${day.precipitationProbability}%"
            else -> tr("Sin lluvia a la vista", "No rain in sight")
        }
        val lines = listOfNotNull(
            WeatherCodes.description(day.code),
            rain + if (rainyHour != null) ". " + tr("Llevá paraguas.", "Bring an umbrella.") else "",
            day.uvIndex?.takeIf { it >= 6 }?.let { tr("UV máximo", "Max UV") + " ${it.roundToInt()} (${WeatherCodes.uvLabel(it)})" },
            day.windMax?.takeIf { it >= 35 }?.let { tr("Viento hasta", "Wind up to") + " ${windText(it)}" },
            Insights.headline(snapshot).takeIf { !tomorrow },
        )
        val builder = NotificationCompat.Builder(context, Notify.CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification_weather)
            .setColor(0xFF3F7BE8.toInt())
            .setContentTitle(title)
            .setContentText(lines.take(2).joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            // Código propio: con el 30 de la hora dorada, un PendingIntent pisaría la ciudad del otro.
            .setContentIntent(Notify.open(context, 32, placeId))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        // Con lluvia probable, el mapa queda a un toque para ver por dónde viene.
        if (rainyHour != null) builder.radarAction(context, placeId, 31)
        val notification = builder
            .muteAction(context, Notify.ID_SUMMARY)
            .build()
        NotificationManagerCompat.from(context).notify(Notify.ID_SUMMARY, notification)
    }
}

/** Corre a la hora del resumen: actualiza si puede, avisa y agenda el de mañana. */
class SummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            try {
                WeatherRepository.get(applicationContext).refreshAll()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (offline: Exception) {
                // Sin conexión: se resume con lo guardado.
            }
            runCatching { SummaryNotifier.notify(applicationContext) }
        } finally {
            // Se encadena detrás de este trabajo (con REPLACE se cancelaría a sí mismo), y siempre:
            // si un día falla, mañana sale igual.
            SummaryNotifier.scheduleAfter(applicationContext, id)
        }
        return Result.success()
    }
}
