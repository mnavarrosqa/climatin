package com.mnavarrosqa.clima.work

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.work.LiveUpdate.requestLive
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * "Hora dorada": un aviso unos 30 minutos antes del atardecer (y del amanecer) con cuenta
 * regresiva. En Android 16 va en vivo, con una barra de la luz dorada y la hora azul.
 */
object SunNotifier {
    private const val CHANNEL_ID = "hora_dorada"
    private const val ID = 5

    private const val GOLD = 0xFFFFB300.toInt()
    private const val BLUE_HOUR = 0xFF3949AB.toInt()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, tr("Hora dorada", "Golden hour"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = tr("Antes del amanecer y del atardecer, para salir a verlo o sacar fotos", "Before sunrise and sunset, to go out and see it or take photos")
            // Es un aviso lindo, no urgente: sin sonido.
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** El próximo amanecer o atardecer, en milisegundos, y si es amanecer. */
    private fun nextEvent(snapshot: WeatherSnapshot, now: Long): Pair<Long, Boolean>? {
        val offset = ZoneOffset.ofTotalSeconds(snapshot.utcOffsetSeconds)
        fun millis(text: String) = runCatching { LocalDateTime.parse(text).toInstant(offset).toEpochMilli() }.getOrNull()
        return snapshot.days.flatMap { day ->
            listOfNotNull(millis(day.sunrise)?.let { it to true }, millis(day.sunset)?.let { it to false })
        }.filter { it.first > now }.minByOrNull { it.first }
    }

    fun check(context: Context) {
        if (!NotifyPrefs.get(context).goldenHour || !RainNotifier.canNotify(context)) {
            if (LiveUpdate.isShowing(context, ID)) NotificationManagerCompat.from(context).cancel(ID)
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
            return
        }
        val snapshot = WeatherRepository.get(context).cached() ?: return
        val now = System.currentTimeMillis()
        scheduleNext(context, snapshot, now)
        val showing = LiveUpdate.isShowing(context, ID)
        val shownFor = NotifyPrefs.time(context, "sol_evento")
        // Ya avisado: se actualiza la barra hasta que pasa.
        if (showing && shownFor > 0) {
            show(context, shownFor, NotifyPrefs.time(context, "sol_amanecer") == 1L, snapshot.place, update = true)
            return
        }
        val (event, sunrise) = nextEvent(snapshot, now) ?: return
        val minutes = (event - now) / 60_000
        // El trabajo periódico corre cada 15 o 30 minutos: la ventana de 50 asegura agarrarlo.
        if (minutes !in 5..50 || shownFor == event || !Notify.allowed(context)) return
        NotifyPrefs.setTime(context, "sol_evento", event)
        NotifyPrefs.setTime(context, "sol_amanecer", if (sunrise) 1L else 0L)
        NotifyPrefs.setTime(context, "sol_origen", now)
        show(context, event, sunrise, snapshot.place, update = false)
    }

    private const val WORK = "hora-dorada"

    /**
     * Agenda una revisión media hora antes del próximo amanecer o atardecer, sin pedir conexión
     * (las horas ya están guardadas): el trabajo periódico puede atrasarse o no correr sin internet.
     */
    private fun scheduleNext(context: Context, snapshot: WeatherSnapshot, now: Long) {
        val shownFor = NotifyPrefs.time(context, "sol_evento")
        val (event, _) = nextEvent(snapshot, maxOf(now, shownFor)) ?: return
        val delay = event - 30 * 60_000L - now
        if (delay < 60_000L) return
        val request = OneTimeWorkRequestBuilder<SunWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun test(context: Context) {
        val now = System.currentTimeMillis()
        // Se guarda como si fuera de verdad: si no, la revisión siguiente la pisaría con otro evento.
        NotifyPrefs.setTime(context, "sol_origen", now)
        NotifyPrefs.setTime(context, "sol_evento", now + 30 * 60_000L)
        NotifyPrefs.setTime(context, "sol_amanecer", 0L)
        show(context, now + 30 * 60_000L, sunrise = false, WeatherRepository.get(context).cached()?.place, update = false)
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, event: Long, sunrise: Boolean, place: String?, update: Boolean) {
        val now = System.currentTimeMillis()
        val time = shortHour(Instant.ofEpochMilli(event).atZone(ZoneId.systemDefault()).toLocalDateTime())
        val title = if (sunrise) tr("Amanece a las $time", "Sunrise at $time") else tr("Atardece a las $time", "Sunset at $time")
        val text = if (sunrise) {
            tr("Hora azul ahora y luz dorada la primera hora", "Blue hour now and golden light for the first hour")
        } else {
            tr("Luz dorada hasta que se pone el sol", "Golden light until the sun goes down")
        } + (place?.let { " · $it" } ?: "")
        val minutes = ((event - now) / 60_000).coerceAtLeast(0)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_weather)
            .setColor(GOLD)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(Notify.open(context, 30, CURRENT_PLACE_ID))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(update)
            // Cuenta regresiva hasta el momento; después ya no (quedaría en negativo).
            .setWhen(event)
            .setShowWhen(event > now)
            .setUsesChronometer(event > now)
            .setChronometerCountDown(true)
            // Se va un rato después de que sale o se pone el sol.
            .setTimeoutAfter((event - now + 20 * 60_000L).coerceAtLeast(60_000L))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .requestLive((if (sunrise) "🌅" else "🌇") + if (event > now) " " + LiveUpdate.chipMinutes(minutes) else "")
        var notification = builder.build()
        if (LiveUpdate.supported) notification = withTimeline(context, notification, event, sunrise, now)
        NotificationManagerCompat.from(context).notify(ID, notification)
    }

    /** Al atardecer: luz dorada hasta que se pone y después la hora azul. Al amanecer, al revés. */
    @RequiresApi(36)
    private fun withTimeline(context: Context, notification: Notification, event: Long, sunrise: Boolean, now: Long): Notification {
        val origin = NotifyPrefs.time(context, "sol_origen").takeIf { it in 1..now } ?: now
        val untilEvent = ((event - origin) / 60_000).coerceAtLeast(1)
        val style = Notification.ProgressStyle()
            .setStyledByProgress(false)
            .addProgressSegment(LiveUpdate.segment(untilEvent, if (sunrise) BLUE_HOUR else GOLD))
            .addProgressSegment(LiveUpdate.segment(20, if (sunrise) GOLD else BLUE_HOUR))
            .addProgressPoint(LiveUpdate.point(untilEvent, 0xFFFF7043.toInt()))
            .setProgress(((now - origin) / 60_000).toInt())
        return LiveUpdate.withProgress(context, notification, style)
    }
}

/** Revisión puntual de la hora dorada (la agenda [SunNotifier]). */
class SunWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runCatching { SunNotifier.check(applicationContext) }
        return Result.success()
    }
}
