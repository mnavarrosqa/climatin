package com.mnavarrosqa.clima.work

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.rainOutlook
import com.mnavarrosqa.clima.data.rainOutlookText
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.data.windText
import com.mnavarrosqa.clima.tile.SalgoTileService
import com.mnavarrosqa.clima.work.LiveUpdate.requestLive
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * "Salgo ahora": durante dos horas, el clima de donde estás en una notificación en vivo (en
 * Android 16, chip con la temperatura en la barra de estado). Se actualiza cada 15 minutos y se
 * termina sola, o con "Terminar". Se empieza desde ajustes rápidos, el acceso directo del ícono o
 * Ajustes → Notificaciones.
 */
object Outing {
    private const val CHANNEL_ID = "salida"
    private const val ID = 6
    private const val WORK = "salida"
    const val ACTION_STOP = "com.mnavarrosqa.clima.TERMINAR_SALIDA"
    const val ACTION_START = "com.mnavarrosqa.clima.SALGO"
    private const val DURATION_MS = 2 * 60 * 60_000L

    /** Hasta cuándo dura la salida en curso (0 si no hay), como estado para Ajustes. */
    var endsAt by mutableStateOf(0L)
        private set
    private var loaded = false

    fun active(context: Context): Boolean {
        if (!loaded) {
            endsAt = NotifyPrefs.time(context, "salida_hasta")
            loaded = true
        }
        return endsAt > System.currentTimeMillis()
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, tr("Salgo ahora", "Heading out"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = tr("El clima de las próximas dos horas mientras estás afuera", "The weather for the next two hours while you're out")
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun start(context: Context) {
        // Sin permiso de notificaciones no hay dónde mostrarlo: no se gasta batería en vano.
        if (!RainNotifier.canNotify(context)) return
        val until = System.currentTimeMillis() + DURATION_MS
        NotifyPrefs.setTime(context, "salida_hasta", until)
        NotifyPrefs.setTime(context, "salida_desde", System.currentTimeMillis())
        endsAt = until
        loaded = true
        // Lo que hay guardado sale ya; el trabajo baja el pronóstico fresco enseguida.
        WeatherRepository.get(context).cached()?.let { show(context, it, until) }
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<OutingWorker>().build())
        SalgoTileService.requestUpdate(context)
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        clear(context)
    }

    /** Lo que deja la salida terminada; el trabajo lo usa sin cancelarse a sí mismo. */
    internal fun clear(context: Context) {
        NotifyPrefs.setTime(context, "salida_hasta", 0L)
        endsAt = 0L
        loaded = true
        NotificationManagerCompat.from(context).cancel(ID)
        SalgoTileService.requestUpdate(context)
    }

    fun toggle(context: Context) = if (active(context)) stop(context) else start(context)

    /** Agenda la próxima actualización (después de que termine la actual). */
    internal fun scheduleNext(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutingWorker>().setInitialDelay(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    @SuppressLint("MissingPermission")
    internal fun show(context: Context, snapshot: WeatherSnapshot, until: Long) {
        if (!RainNotifier.canNotify(context)) return
        val now = System.currentTimeMillis()
        val current = snapshot.current
        val outlook = snapshot.rainOutlook(withinMinutes = 120)
        val rain = outlook?.let(::rainOutlookText) ?: tr("Sin lluvia a la vista", "No rain in sight")
        val endTime = shortHour(Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()).toLocalDateTime())
        val hours = snapshot.upcomingHours(4).drop(1).take(3).joinToString("   ") { hour ->
            shortHour(hour.dateTime) + " " + hour.temperature.degrees() +
                (hour.precipitationProbability?.takeIf { it >= 20 }?.let { " ☔$it%" } ?: "")
        }
        val title = "${current.temperature.degrees()} ${WeatherCodes.description(current.code)} · $rain"
        val details = listOf(
            tr("Sensación", "Feels like") + " ${current.feelsLike.degrees()}",
            tr("Viento", "Wind") + " ${windText(current.windSpeed)}",
        ).joinToString(" · ")
        val stop = PendingIntent.getBroadcast(
            context,
            ID,
            Intent(context, NotificationActionReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_outing)
            .setColor(0xFF3F7BE8.toInt())
            .setContentTitle(title)
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$details\n$hours\n" + tr("Seguimiento hasta las $endTime", "Tracking until $endTime")))
            .setSubText(snapshot.place)
            .setContentIntent(Notify.open(context, 40, CURRENT_PLACE_ID))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setTimeoutAfter((until - now).coerceAtLeast(60_000))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, tr("Terminar", "Stop"), stop)
            .requestLive(current.temperature.degrees())
        var notification = builder.build()
        if (LiveUpdate.supported) notification = withProgress(context, notification, until, now)
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    /** Barra de las dos horas de salida, con un punto por cada hora. */
    @RequiresApi(36)
    private fun withProgress(context: Context, notification: Notification, until: Long, now: Long): Notification {
        val since = NotifyPrefs.time(context, "salida_desde").takeIf { it in 1..now } ?: now
        val total = ((until - since) / 60_000).coerceAtLeast(1)
        val style = Notification.ProgressStyle()
            .setStyledByProgress(true)
            .addProgressSegment(LiveUpdate.segment(total, 0xFF3F7BE8.toInt()))
            .addProgressPoint(LiveUpdate.point(60, 0xFF7FB0F5.toInt()))
            .setProgress(((now - since) / 60_000).toInt().coerceIn(0, total.toInt()))
        return LiveUpdate.withProgress(context, notification, style)
    }
}

/** Baja el clima de donde estás y actualiza la notificación de la salida; se vuelve a agendar. */
class OutingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val until = NotifyPrefs.time(context, "salida_hasta")
        if (until <= System.currentTimeMillis()) {
            Outing.clear(context)
            return Result.success()
        }
        val repository = WeatherRepository.get(context)
        val snapshot = repository.refreshCurrent().getOrNull() ?: repository.cached()
        // Mientras bajaba se pudo tocar "Terminar" (o empezar otra salida): no se revive.
        if (isStopped || NotifyPrefs.time(context, "salida_hasta") != until) return Result.success()
        snapshot?.let { runCatching { Outing.show(context, it, until) } }
        Outing.scheduleNext(context)
        return Result.success()
    }
}
