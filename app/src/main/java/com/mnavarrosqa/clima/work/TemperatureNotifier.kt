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
import com.mnavarrosqa.clima.MainActivity
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.tr

/**
 * Notificación fija con la temperatura como ícono de la barra de estado, al lado de la hora.
 * Es silenciosa y de prioridad baja: no suena ni aparece arriba de las apps.
 */
object TemperatureNotifier {
    private const val CHANNEL_ID = "temperatura"
    private const val NOTIFICATION_ID = 3

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
        val intent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val details = listOfNotNull(
            today?.let { tr("Máx", "High") + " ${it.max.degrees()} · " + tr("Mín", "Low") + " ${it.min.degrees()}" },
            snapshot.place,
        ).joinToString(" · ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(IconCompat.createWithBitmap(temperatureIcon(current.temperature.degrees())))
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
