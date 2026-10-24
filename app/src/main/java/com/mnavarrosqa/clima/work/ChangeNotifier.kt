package com.mnavarrosqa.clima.work

import com.mnavarrosqa.clima.data.tr
import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.degreesDelta
import com.mnavarrosqa.clima.data.windText
import com.mnavarrosqa.clima.work.Notify.muteAction
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Cambios bruscos para donde estás, cada uno como mucho una vez por día y en un horario que tenga
 * sentido: el bajón de temperatura de mañana desde el mediodía, la helada a la tarde, el viento
 * fuerte de día y el UV muy alto a la mañana.
 */
object ChangeNotifier {
    private enum class Kind(val key: String, val id: Int) {
        TEMPERATURE("temperatura", Notify.ID_CHANGES_BASE),
        FROST("helada", Notify.ID_CHANGES_BASE + 1),
        WIND("viento", Notify.ID_CHANGES_BASE + 2),
        UV("uv", Notify.ID_CHANGES_BASE + 3),
    }

    fun check(context: Context) {
        val settings = NotifyPrefs.get(context)
        if (!settings.changes || !Notify.allowed(context)) return
        val snapshot = WeatherRepository.get(context).cached() ?: return
        val days = snapshot.upcomingDays(2)
        val today = days.firstOrNull() ?: return
        val tomorrow = days.getOrNull(1)
        val hour = snapshot.nowAtPlace().hour

        fun once(kind: Kind, enabled: Boolean, window: IntRange, condition: Boolean, title: () -> String, text: () -> String) {
            if (!enabled || hour !in window || !condition || NotifyPrefs.alreadyToday(context, kind.key)) return
            show(context, kind.id, title(), text())
            NotifyPrefs.markToday(context, kind.key)
        }

        if (tomorrow != null) {
            val delta = tomorrow.max - today.max
            once(
                Kind.TEMPERATURE, settings.changeTemperature, 12..21, abs(delta) >= 6,
                title = {
                    if (delta < 0) tr("Mañana ${delta.degreesDelta()} más fresco", "Tomorrow ${delta.degreesDelta()} cooler")
                    else tr("Mañana ${delta.degreesDelta()} más caluroso", "Tomorrow ${delta.degreesDelta()} warmer")
                },
                text = { tr("Máxima de ${tomorrow.max.degrees()} (hoy ${today.max.degrees()})", "High of ${tomorrow.max.degrees()} (today ${today.max.degrees()})") },
            )
            once(
                Kind.FROST, settings.changeFrost, 16..22, tomorrow.min <= 1.0,
                title = { tr("Posible helada esta noche", "Possible frost tonight") },
                text = { tr("Mínima de ${tomorrow.min.degrees()}: cubrí las plantas y el auto", "Low of ${tomorrow.min.degrees()}: cover plants and the car") },
            )
        }
        today.windMax?.let { wind ->
            once(
                Kind.WIND, settings.changeWind, 6..19, wind >= 50,
                title = { tr("Viento fuerte hoy", "Strong wind today") },
                text = { tr("Hasta ${windText(wind)}. Cuidado con objetos sueltos", "Up to ${windText(wind)}. Watch out for loose objects") },
            )
        }
        today.uvIndex?.let { uv ->
            once(
                Kind.UV, settings.changeUv, 7..12, uv >= 8,
                title = { tr("UV muy alto hoy: ${uv.roundToInt()}", "Very high UV today: ${uv.roundToInt()}") },
                text = { tr("Protector, anteojos y sombra entre las 11 y las 16", "Sunscreen, sunglasses and shade from 11 AM to 4 PM") },
            )
        }
    }

    fun test(context: Context) {
        show(
            context,
            Kind.TEMPERATURE.id,
            tr("Mañana 8° más fresco", "Tomorrow 8° cooler"),
            tr("Esto es un aviso de prueba de cambios bruscos.", "This is a test of big-change alerts."),
        )
    }

    @SuppressLint("MissingPermission")
    private fun show(context: Context, id: Int, title: String, text: String) {
        if (!RainNotifier.canNotify(context)) return
        val notification = NotificationCompat.Builder(context, Notify.CHANNEL_CHANGES)
            .setSmallIcon(R.drawable.ic_notification_weather)
            .setColor(0xFFE8833A.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(Notify.open(context, 40 + id, CURRENT_PLACE_ID))
            .setAutoCancel(true)
            .muteAction(context, id)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
