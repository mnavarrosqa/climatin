package com.mnavarrosqa.clima.widget

import com.mnavarrosqa.clima.data.tr
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.longDate

/**
 * En Android 15+ el selector de widgets puede mostrar una vista previa con datos reales en vez de
 * los de ejemplo. Se arma con los mismos layouts de vista previa, cambiando los textos. El sistema
 * limita cuántas veces se puede publicar, así que se hace como mucho una vez cada 6 horas.
 */
object WidgetPreviews {
    private const val KEY = "previews_published_at"
    private const val INTERVAL_MS = 6 * 60 * 60_000L

    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val prefs = context.getSharedPreferences("clima", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY, 0L) < INTERVAL_MS) return
        val snapshot = WeatherRepository.get(context).cached() ?: return
        val current = snapshot.current
        val today = snapshot.today()
        val icon = WeatherCodes.icon(current.code, current.isDay)
        val description = WeatherCodes.description(current.code)
        val place = snapshot.place ?: tr("Tu ubicación", "Your location")
        val minMax = today?.let { tr("Máx", "High") + " ${it.max.degrees()} · " + tr("Mín", "Low") + " ${it.min.degrees()}" } ?: ""

        val clima = RemoteViews(context.packageName, R.layout.widget_preview_clima).apply {
            setTextViewText(R.id.preview_place, place)
            setTextViewText(R.id.preview_temp, current.temperature.degrees())
            setTextViewText(R.id.preview_desc, description)
            setTextViewText(R.id.preview_minmax, minMax)
            setImageViewResource(R.id.preview_icon, icon)
        }
        val vistazo = RemoteViews(context.packageName, R.layout.widget_preview_vistazo).apply {
            setTextViewText(R.id.preview_date, longDate(snapshot.nowAtPlace().toLocalDate()))
            setTextViewText(R.id.preview_line, "${current.temperature.degrees()} $description · $place")
            setImageViewResource(R.id.preview_icon, icon)
        }
        val pildora = RemoteViews(context.packageName, R.layout.widget_preview_pildora).apply {
            setTextViewText(R.id.preview_temp, current.temperature.degrees())
            setTextViewText(R.id.preview_desc, description)
            setTextViewText(R.id.preview_minmax, today?.let { "${it.max.degrees()} / ${it.min.degrees()}" } ?: "")
            setImageViewResource(R.id.preview_icon, icon)
        }
        val manager = AppWidgetManager.getInstance(context)
        runCatching {
            listOf(
                ClimaWidgetReceiver::class.java to clima,
                VistazoWidgetReceiver::class.java to vistazo,
                PildoraWidgetReceiver::class.java to pildora,
            ).map { (receiver, views) ->
                manager.setWidgetPreview(
                    ComponentName(context, receiver),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    views,
                )
            }.let { accepted ->
                // Si el sistema rechazó alguna (tiene un límite de publicaciones), se reintenta
                // la próxima vez en lugar de esperar 6 horas.
                if (accepted.all { it }) prefs.edit().putLong(KEY, now).apply()
            }
        }
    }
}
