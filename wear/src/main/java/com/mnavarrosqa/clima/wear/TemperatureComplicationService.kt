package com.mnavarrosqa.clima.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.degrees

/** Complicación para la esfera: temperatura (corta) o temperatura y estado (larga). */
class TemperatureComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val current = WearStore.cached(this)?.current ?: return NoDataComplicationData()
        return data(request.complicationType, current.temperature.degrees(), WeatherCodes.description(current.code))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, "22°", WeatherCodes.description(2))

    private fun data(type: ComplicationType, temperature: String, description: String): ComplicationData? {
        val full = PlainComplicationText.Builder("$temperature $description").build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(PlainComplicationText.Builder(temperature).build(), full).build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData.Builder(full, full).build()
            else -> null
        }
    }
}
