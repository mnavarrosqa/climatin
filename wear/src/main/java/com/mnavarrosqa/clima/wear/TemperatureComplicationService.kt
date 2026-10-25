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
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.degrees

/**
 * Complicación para la esfera: ícono y temperatura (corta) o temperatura y estado (larga). Tocarla
 * abre la app. Con datos de más de 6 horas no muestra nada: una temperatura vieja engaña.
 */
class TemperatureComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = WearStore.cached(this) ?: return NoDataComplicationData()
        if (System.currentTimeMillis() - snapshot.fetchedAt > 6 * 60 * 60_000L) return NoDataComplicationData()
        val current = snapshot.current
        return data(
            request.complicationType,
            current.temperature.degrees(),
            WeatherCodes.description(current.code),
            WeatherCodes.icon(current.code, current.isDay),
        )
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, "22°", WeatherCodes.description(2), WeatherCodes.icon(2, true))

    private fun data(type: ComplicationType, temperature: String, description: String, iconRes: Int): ComplicationData? {
        val full = PlainComplicationText.Builder("$temperature $description").build()
        val image = MonochromaticImage.Builder(Icon.createWithResource(this, iconRes)).build()
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
            else -> null
        }
    }
}
