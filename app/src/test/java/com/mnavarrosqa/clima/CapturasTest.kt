package com.mnavarrosqa.clima

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.ui.toGradient
import com.mnavarrosqa.clima.wallpaper.WallpaperPreview
import com.mnavarrosqa.clima.wallpaper.WallpaperScene
import com.mnavarrosqa.clima.wallpaper.WallpaperStyle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.mnavarrosqa.clima.data.Current
import com.mnavarrosqa.clima.data.Day
import com.mnavarrosqa.clima.data.Hour
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.ui.AnimatedSky
import com.mnavarrosqa.clima.ui.ForecastContent
import com.mnavarrosqa.clima.ui.theme.ClimaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Capturas de la pantalla principal en teléfono, plegable y tablet, en claro, oscuro, español e
 * inglés. Sirven para la ficha de Play y para ver si un cambio rompió el diseño. No fallan por
 * diferencias de imagen: solo graban.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Una Application vacía: la de la app agenda WorkManager, que en Robolectric no está iniciado.
@Config(sdk = [34], application = Application::class)
class CapturasTest {

    @get:Rule
    val compose = createComposeRule()

    private val now: LocalDateTime = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS)

    private fun sample(): WeatherSnapshot {
        val codes = listOf(2, 2, 3, 3, 61, 63, 61, 3, 2, 1, 1, 0)
        val hours = (0 until 24).map { i ->
            Hour(
                time = now.plusHours(i.toLong()).toString(),
                temperature = 18.0 + 6 * kotlin.math.sin(i / 24.0 * 2 * Math.PI),
                code = codes[i % codes.size],
                precipitationProbability = if (codes[i % codes.size] >= 61) 70 else 5,
                isDay = (now.plusHours(i.toLong()).hour) in 7..19,
            )
        }
        val days = (0 until 10).map { i ->
            val date = now.toLocalDate().plusDays(i.toLong())
            Day(
                date = date.toString(),
                code = listOf(2, 61, 3, 0, 1, 80, 2, 0, 3, 95)[i],
                max = 24.0 - (i % 4) * 2,
                min = 12.0 - (i % 3),
                sunrise = date.atTime(6, 42).toString(),
                sunset = date.atTime(19, 58).toString(),
                uvIndex = 7.0,
                precipitationProbability = listOf(10, 80, 30, 0, 5, 60, 10, 0, 20, 90)[i],
            )
        }
        return WeatherSnapshot(
            place = "Córdoba",
            latitude = -31.4,
            longitude = -64.2,
            utcOffsetSeconds = 0,
            fetchedAt = System.currentTimeMillis(),
            current = Current(now.toString(), 22.0, 21.0, 48, true, 2, 12.0, 0.0, 1013.0, 24000.0, 6.0, 140.0),
            hours = hours,
            days = days,
        )
    }

    private fun capture(name: String, language: String) {
        Locale.setDefault(Locale.forLanguageTag(language))
        compose.mainClock.autoAdvance = false
        val snapshot = sample()
        compose.setContent {
            ClimaTheme {
                Box(Modifier.fillMaxSize()) {
                    AnimatedSky(WeatherCodes.kind(snapshot.current.code), snapshot.current.isDay, Modifier.fillMaxSize())
                    ForecastContent(
                        snapshot = snapshot,
                        isCurrentLocation = true,
                        error = null,
                        onDay = {},
                        onOpenRadar = { _, _ -> },
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onRoot().captureRoboImage("build/capturas/$name.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun telefono() = capture("telefono-es", "es-AR")

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun telefonoOscuro() = capture("telefono-oscuro-es", "es-AR")

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun telefonoIngles() = capture("telefono-en", "en-US")

    @Test
    @Config(qualifiers = "w841dp-h900dp-xhdpi")
    fun plegable() = capture("plegable-es", "es-AR")

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tablet() = capture("tablet-es", "es-AR")

    /** Los cuatro diseños del fondo de pantalla, de día con algo de nubes y de noche con lluvia. */
    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun fondos() {
        Locale.setDefault(Locale.forLanguageTag("es-AR"))
        compose.mainClock.autoAdvance = false
        val scenes = listOf(
            WallpaperScene(WeatherKind.PARTLY, true, skyColors(WeatherKind.PARTLY, true).toGradient(), "21°", "Córdoba · Algo nublado"),
            WallpaperScene(WeatherKind.RAIN, false, skyColors(WeatherKind.RAIN, false).toGradient()),
        )
        compose.setContent {
            ClimaTheme {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    scenes.forEach { scene ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WallpaperStyle.entries.forEach { style ->
                                WallpaperPreview(scene, style, touch = false, modifier = Modifier.weight(1f).aspectRatio(9f / 19.5f))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("build/capturas/fondos.png")
    }
}
