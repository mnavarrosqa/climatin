package com.mnavarrosqa.clima.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.tr

/**
 * Complicación para la esfera: ícono y temperatura (corta), temperatura y estado (larga) o la
 * temperatura dentro del rango de hoy, de la mínima a la máxima (rango). Tocarla abre la app. Con
 * datos de más de 6 horas no muestra nada: una temperatura vieja engaña.
 */
class TemperatureComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = WearStore.cached(this) ?: return NoDataComplicationData()
        if (System.currentTimeMillis() - snapshot.fetchedAt > 6 * 60 * 60_000L) return NoDataComplicationData()
        val current = snapshot.withCurrentFromHours().current
        val today = snapshot.today()
        return data(
            request.complicationType,
            Reading(
                temperature = current.temperature,
                code = current.code,
                isDay = current.isDay,
                min = today?.min,
                max = today?.max,
            ),
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, Reading(temperature = 22.0, code = 2, isDay = true, min = 14.0, max = 25.0))

    /** Lo que muestra la complicación, en °C (degrees() lo pasa a la unidad elegida). */
    private class Reading(val temperature: Double, val code: Int, val isDay: Boolean, val min: Double?, val max: Double?)

    private fun data(type: ComplicationType, reading: Reading): ComplicationData? {
        val temperature = reading.temperature.degrees()
        val description = WeatherCodes.description(reading.code)
        val full = PlainComplicationText.Builder("$temperature $description").build()
        // La esfera tiñe la imagen de un solo color: va la versión monocromática del ícono.
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, monochromeIcon(reading.code, reading.isDay))).build()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(temperature).build(), full)
                    .setMonochromaticImage(image)
                    .setTapAction(open)
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(full, full)
                    .setMonochromaticImage(image)
                    .setTapAction(open)
                    .build()
            ComplicationType.RANGED_VALUE -> {
                // El arco va de la mínima a la máxima de hoy. Sin el día (o con las dos iguales) se
                // abre un margen alrededor de la actual para que el rango sea válido.
                val now = reading.temperature.toFloat()
                val low = reading.min?.toFloat() ?: (now - 5f)
                val high = reading.max?.toFloat() ?: (now + 5f)
                var min = minOf(low, high)
                var max = maxOf(low, high)
                if (max - min < 1f) {
                    min -= 0.5f
                    max += 0.5f
                }
                val range = (reading.min?.degrees() ?: "") + " – " + (reading.max?.degrees() ?: "")
                RangedValueComplicationData.Builder(
                    // La hora actual puede pasarse del pronóstico del día: se recorta al arco.
                    now.coerceIn(min, max),
                    min,
                    max,
                    PlainComplicationText.Builder("$temperature $description. " + tr("Hoy", "Today") + " $range").build(),
                )
                    .setText(PlainComplicationText.Builder(temperature).build())
                    .setMonochromaticImage(image)
                    .setTapAction(open)
                    .build()
            }
            else -> null
        }
    }
}
