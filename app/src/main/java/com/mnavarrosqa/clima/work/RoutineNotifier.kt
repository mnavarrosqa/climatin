package com.mnavarrosqa.clima.work

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
import com.mnavarrosqa.clima.data.Hour
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.degreesDelta
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.data.windText
import com.mnavarrosqa.clima.work.Notify.muteAction
import com.mnavarrosqa.clima.work.Notify.radarAction
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * "Mi rutina": una hora antes de salir, el clima de la ida y de la vuelta para donde estás, con
 * lo que conviene llevar (paraguas, abrigo para la vuelta, protector). Solo los días elegidos.
 */
object RoutineNotifier {
    private const val WORK = "mi-rutina"

    /** Cuánto antes de salir avisar: da tiempo a vestirse y a que el trabajo actualice antes. */
    private const val LEAD_MINUTES = 60L

    private fun dayAllowed(date: LocalDate, weekdays: Boolean) =
        !weekdays || (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY)

    /** Agenda el próximo aviso (o lo cancela si está apagado). Con la hora del teléfono, como el resumen. */
    fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val settings = NotifyPrefs.get(context)
        val work = WorkManager.getInstance(context)
        if (!settings.routine) {
            work.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        // Con salida a las 0 el aviso cae el día anterior: se mira por día de salida, no de aviso.
        val next = (0L..8L).asSequence()
            .map { now.toLocalDate().plusDays(it) }
            .filter { dayAllowed(it, settings.routineWeekdays) }
            .map { it.atTime(settings.routineLeave, 0).minusMinutes(LEAD_MINUTES) }
            .firstOrNull { it.isAfter(now) } ?: return
        val request = OneTimeWorkRequestBuilder<RoutineWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        work.enqueueUniqueWork(WORK, policy, request)
    }

    /** La salida que toca avisar ahora: la próxima, si falta poco (el trabajo pudo atrasarse). */
    private fun upcomingLeave(settings: NotifySettings, now: LocalDateTime): LocalDateTime? =
        (0L..1L).map { now.toLocalDate().plusDays(it).atTime(settings.routineLeave, 0) }
            .firstOrNull { !it.isBefore(now.minusMinutes(10)) }
            ?.takeIf { Duration.between(now, it).toMinutes() <= LEAD_MINUTES + 30 }

    fun notify(context: Context) {
        val settings = NotifyPrefs.get(context)
        if (!settings.routine || NotifyPrefs.isMuted(context) || !RainNotifier.canNotify(context)) return
        val leave = upcomingLeave(settings, LocalDateTime.now()) ?: return
        if (!dayAllowed(leave.toLocalDate(), settings.routineWeekdays)) return
        // Una vez por salida: si se reagenda el mismo día (cambió la vuelta), no se repite.
        val leaveMillis = leave.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (NotifyPrefs.time(context, "rutina_avisada") == leaveMillis) return
        val snapshot = WeatherRepository.get(context).cached() ?: return
        if (show(context, snapshot, settings, leave, silent = NotifyPrefs.inQuietHours(context))) {
            NotifyPrefs.setTime(context, "rutina_avisada", leaveMillis)
        }
    }

    /** Ejemplo para Ajustes → Probar: la próxima salida con el pronóstico guardado. */
    fun test(context: Context) {
        val settings = NotifyPrefs.get(context)
        val snapshot = WeatherRepository.get(context).cached() ?: return
        val now = LocalDateTime.now()
        var leave = now.toLocalDate().atTime(settings.routineLeave, 0)
        if (leave.isBefore(now)) leave = leave.plusDays(1)
        show(context, snapshot, settings, leave, silent = false)
    }

    /** Hora del pronóstico en el lugar para un momento del teléfono (casi siempre son la misma). */
    private fun atPlace(snapshot: WeatherSnapshot, time: LocalDateTime): LocalDateTime =
        time.atZone(ZoneId.systemDefault()).withZoneSameInstant(snapshot.zone).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)

    /** El clima de un momento: la hora en punto y la siguiente (la lluvia de cada hora es la de la hora anterior). */
    private class Moment(val hour: Hour, val rain: Int, val wind: Double)

    private fun moment(snapshot: WeatherSnapshot, time: LocalDateTime): Moment? {
        val target = atPlace(snapshot, time)
        val index = snapshot.hours.indexOfFirst { it.dateTime == target }
        if (index < 0) return null
        val hour = snapshot.hours[index]
        val around = snapshot.hours.subList(index, minOf(snapshot.hours.size, index + 2))
        return Moment(
            hour = hour,
            rain = around.maxOf { it.precipitationProbability ?: 0 },
            wind = around.maxOf { it.windGusts ?: it.windSpeed ?: 0.0 },
        )
    }

    private fun Moment.feels(): Double = hour.feelsLike ?: hour.temperature

    private fun Moment.line(label: String, time: LocalDateTime): String {
        val feels = hour.feelsLike
        val temperature = hour.temperature.degrees() +
            if (feels != null && (feels - hour.temperature).let { it >= 2 || it <= -2 }) " (" + tr("sensación", "feels") + " ${feels.degrees()})" else ""
        val rainText = if (rain >= 30) " · " + tr("lluvia", "rain") + " $rain%" else ""
        return "$label ${shortHour(time)} · $temperature, ${WeatherCodes.description(hour.code).lowercase()}$rainText"
    }

    /** Devuelve si salió el aviso. */
    @SuppressLint("MissingPermission")
    private fun show(context: Context, snapshot: WeatherSnapshot, settings: NotifySettings, leave: LocalDateTime, silent: Boolean): Boolean {
        if (!RainNotifier.canNotify(context)) return false
        // Vuelta antes (o a la misma hora) que la salida: es al día siguiente (turno de noche).
        var back = leave.toLocalDate().atTime(settings.routineBack, 0)
        if (!back.isAfter(leave)) back = back.plusDays(1)
        val going = moment(snapshot, leave)
        val coming = moment(snapshot, back)
        if (going == null && coming == null) return false

        val tips = buildList {
            val rainy = listOfNotNull(
                going?.takeIf { it.rain >= 50 }?.let { tr("a la ida", "going") },
                coming?.takeIf { it.rain >= 50 }?.let { tr("a la vuelta", "coming back") },
            )
            if (rainy.isNotEmpty()) add(tr("Llevá paraguas: lluvia probable ", "Bring an umbrella: rain likely ") + rainy.joinToString(tr(" y ", " and ")))
            if (going != null && coming != null) {
                val change = coming.feels() - going.feels()
                if (change <= -6) add(tr("Llevá abrigo para la vuelta: refresca ${(-change).degreesDelta()}", "Bring a jacket for later: ${(-change).degreesDelta()} cooler"))
                else if (change >= 8) add(tr("Vestite en capas: a la vuelta hace ${change.degreesDelta()} más", "Dress in layers: ${change.degreesDelta()} warmer later"))
            }
            val wind = maxOf(going?.wind ?: 0.0, coming?.wind ?: 0.0)
            if (wind >= 55) add(tr("Viento fuerte: ráfagas de hasta ${windText(wind)}", "Strong wind: gusts up to ${windText(wind)}"))
            // UV al caminar en esos horarios (el del mediodía ya lo avisa el aviso de UV).
            val uv = maxOf(going?.hour?.uvIndex ?: 0.0, coming?.hour?.uvIndex ?: 0.0)
            if (uv >= 6) add(tr("Protector: UV ${uv.roundToInt()} en el camino", "Sunscreen: UV ${uv.roundToInt()} on the way"))
        }
        val leaveTemp = going?.hour?.temperature?.degrees()
        val backTemp = coming?.hour?.temperature?.degrees()
        val title = when {
            leaveTemp != null && backTemp != null -> tr("Salís con $leaveTemp y volvés con $backTemp", "$leaveTemp when you leave, $backTemp when you're back")
            leaveTemp != null -> tr("Salís con $leaveTemp", "$leaveTemp when you leave")
            else -> tr("Volvés con $backTemp", "$backTemp when you're back")
        }
        val lines = listOfNotNull(
            going?.line(tr("Ida", "Going"), leave),
            coming?.line(tr("Vuelta", "Back"), back),
        ) + tips
        val summary = tips.firstOrNull() ?: tr("Sin lluvia ni sorpresas en el camino", "No rain or surprises on the way")
        val builder = NotificationCompat.Builder(context, Notify.CHANNEL_ROUTINE)
            .setSmallIcon(R.drawable.ic_notification_weather)
            .setColor(0xFF3F7BE8.toInt())
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(Notify.open(context, 60, CURRENT_PLACE_ID))
            .setAutoCancel(true)
            // En horario de silencio (salida temprano) llega igual, pero sin sonar.
            .setSilent(silent)
            // Pasada la vuelta ya no sirve.
            .setTimeoutAfter(Duration.between(LocalDateTime.now(), back).toMillis().coerceAtLeast(60_000L))
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        if ((going?.rain ?: 0) >= 50 || (coming?.rain ?: 0) >= 50) builder.radarAction(context, CURRENT_PLACE_ID, 61)
        val notification = builder.muteAction(context, Notify.ID_ROUTINE).build()
        NotificationManagerCompat.from(context).notify(Notify.ID_ROUTINE, notification)
        return true
    }
}

/** Corre antes de la salida: actualiza donde estás si puede, avisa y agenda la próxima. */
class RoutineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            try {
                // Solo "donde estoy": la rutina no mira las ciudades guardadas.
                WeatherRepository.get(applicationContext).refreshAll(cities = false)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (offline: Exception) {
                // Sin conexión: se avisa con lo guardado.
            }
            runCatching { RoutineNotifier.notify(applicationContext) }
        } finally {
            // Igual que el resumen: encadenado detrás de este trabajo y siempre, aunque falle.
            RoutineNotifier.schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }
}
