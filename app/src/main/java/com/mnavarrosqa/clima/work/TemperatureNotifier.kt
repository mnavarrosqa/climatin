package com.mnavarrosqa.clima.work

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import android.widget.RemoteViews
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.shortHour
import com.mnavarrosqa.clima.work.Notify.refreshAction
import com.mnavarrosqa.clima.work.LiveUpdate.requestLive
import com.mnavarrosqa.clima.ClimaActivity
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.tr

/**
 * Notificación fija con la temperatura como ícono de la barra de estado, al lado de la hora.
 * Es silenciosa y de prioridad baja: no suena ni aparece arriba de las apps.
 */
object TemperatureNotifier {
    private const val CHANNEL_ID = "temperatura"
    private const val NOTIFICATION_ID = Notify.ID_STATUS

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            tr("Temperatura en la barra de estado", "Temperature in the status bar"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = tr("Muestra los grados al lado de la hora", "Shows the temperature next to the clock")
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun update(context: Context) {
        val repository = WeatherRepository.get(context)
        val manager = NotificationManagerCompat.from(context)
        if (!repository.statusTemperatureEnabled || !RainNotifier.canNotify(context)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val snapshot = repository.cached() ?: return
        val current = snapshot.current
        val today = snapshot.today()
        val intent = Notify.open(context, 2, CURRENT_PLACE_ID)
        val details = listOfNotNull(
            today?.let { tr("Máx", "High") + " ${it.max.degrees()} · " + tr("Mín", "Low") + " ${it.min.degrees()}" },
            snapshot.place,
        ).joinToString(" · ")
        // Al expandirla: las próximas seis horas con su ícono, como un widget chico.
        val hours = snapshot.upcomingHours(6)
        val expanded = RemoteViews(context.packageName, R.layout.notification_hours).apply {
            setTextViewText(R.id.hours_title, Insights.headline(snapshot) ?: details)
            hours.forEachIndexed { i, hour ->
                setTextViewText(HOUR_TIME[i], if (i == 0) tr("Ahora", "Now") else shortHour(hour.dateTime))
                setImageViewResource(HOUR_ICON[i], WeatherCodes.icon(hour.code, hour.isDay))
                setTextViewText(HOUR_TEMP[i], hour.temperature.degrees())
            }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(IconCompat.createWithBitmap(temperatureIcon(current.temperature.degrees())))
        // Android 16, si se eligió: chip con los grados (Live Update). Un Live Update no puede
        // tener diseño propio, así que las próximas horas van como texto.
        if (LiveUpdate.supported && NotifyPrefs.get(context).liveTemperature) {
            val line = hours.mapIndexed { i, hour ->
                (if (i == 0) tr("Ahora", "Now") else shortHour(hour.dateTime)) + " " + hour.temperature.degrees()
            }.joinToString("   ")
            builder.setStyle(NotificationCompat.BigTextStyle().bigText((Insights.headline(snapshot) ?: details) + "\n" + line))
                .requestLive(chipText(snapshot))
        } else {
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(expanded)
        }
        val notification = builder
            .refreshAction(context, NOTIFICATION_ID)
            .setContentTitle("${current.temperature.degrees()} ${WeatherCodes.description(current.code)}")
            .setContentText(Insights.headline(snapshot) ?: details)
            .setSubText(details.takeIf { Insights.headline(snapshot) != null })
            .setContentIntent(intent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * El ícono chico ya muestra los grados de ahora: el texto del chip dice lo que viene. Lluvia
     * en las próximas 3 horas ("☔ 16:00"), si no hacia dónde va la temperatura ("↓ 12°" o "↑ 19°").
     */
    private fun chipText(snapshot: WeatherSnapshot): String {
        val next = snapshot.upcomingHours(12).drop(1)
        next.take(3).firstOrNull { (it.precipitationProbability ?: 0) >= 50 }?.let { return "☔ " + shortHour(it.dateTime) }
        val now = snapshot.current.temperature
        val max = next.maxByOrNull { it.temperature } ?: return now.degrees()
        val min = next.minByOrNull { it.temperature } ?: return now.degrees()
        // Lo que esté más lejos de ahora: a la noche suele ser la mínima, a la mañana la máxima.
        return if (max.temperature - now >= now - min.temperature) "↑ " + max.temperature.degrees() else "↓ " + min.temperature.degrees()
    }

    private val HOUR_TIME = intArrayOf(R.id.hour_time_0, R.id.hour_time_1, R.id.hour_time_2, R.id.hour_time_3, R.id.hour_time_4, R.id.hour_time_5)
    private val HOUR_ICON = intArrayOf(R.id.hour_icon_0, R.id.hour_icon_1, R.id.hour_icon_2, R.id.hour_icon_3, R.id.hour_icon_4, R.id.hour_icon_5)
    private val HOUR_TEMP = intArrayOf(R.id.hour_temp_0, R.id.hour_temp_1, R.id.hour_temp_2, R.id.hour_temp_3, R.id.hour_temp_4, R.id.hour_temp_5)

    /** El número dibujado en blanco: Android lo pinta como ícono de la barra de estado. */
    private fun temperatureIcon(text: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 112f
        }
        // Se achica la letra hasta que entre (por ejemplo, "-12°").
        while (paint.measureText(text) > size - 4 && paint.textSize > 40f) paint.textSize -= 4f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(text, size / 2f, y, paint)
        return bitmap
    }
}
