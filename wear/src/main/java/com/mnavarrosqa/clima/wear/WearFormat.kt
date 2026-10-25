package com.mnavarrosqa.clima.wear

import androidx.annotation.DrawableRes
import com.mnavarrosqa.clima.data.Lang
import com.mnavarrosqa.clima.data.Units
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.shortHour
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Hora corta para las columnas de "próximas horas": "14:00" o "2 p. m.". En 12 h se saca ":00"
 * porque en la pantalla del reloj no entra.
 */
fun compactHour(time: LocalDateTime): String =
    if (Units.current.use24h) shortHour(time) else time.format(DateTimeFormatter.ofPattern("h a", Lang.locale))

/**
 * Íconos de un solo color para la complicación. La esfera tiñe la imagen entera con un color, así
 * que los de colores (sol sobre nube) quedaban como una mancha; estos separan las formas.
 */
@DrawableRes
fun monochromeIcon(code: Int, isDay: Boolean): Int = when (WeatherCodes.kind(code)) {
    WeatherKind.CLEAR -> if (isDay) R.drawable.ic_mono_clear_day else R.drawable.ic_mono_clear_night
    WeatherKind.PARTLY -> if (isDay) R.drawable.ic_mono_partly_cloudy_day else R.drawable.ic_mono_partly_cloudy_night
    WeatherKind.CLOUDY -> R.drawable.ic_mono_cloudy
    WeatherKind.FOG -> R.drawable.ic_mono_fog
    WeatherKind.DRIZZLE -> R.drawable.ic_mono_drizzle
    WeatherKind.RAIN -> R.drawable.ic_mono_rain
    WeatherKind.SNOW -> R.drawable.ic_mono_snow
    WeatherKind.STORM -> R.drawable.ic_mono_thunderstorm
}
