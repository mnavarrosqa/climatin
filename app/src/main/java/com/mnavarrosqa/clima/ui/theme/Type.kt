package com.mnavarrosqa.clima.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.mnavarrosqa.clima.R

/**
 * Fuentes descargables de Google Fonts (las baja Play Services una sola vez y las comparte con
 * otras apps, así que no suman peso a la app). Primero Google Sans Flex; si no está disponible,
 * Roboto Flex; y si no hay Play Services, la fuente del sistema.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val weights = listOf(FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)

private fun family(vararg names: String) = FontFamily(
    names.flatMap { name -> weights.map { Font(GoogleFont(name), provider, it) } },
)

/** Textos de lectura. */
val Flex = family("Google Sans Flex", "Roboto Flex")

private fun TextStyle.flex(weight: FontWeight? = null) = copy(fontFamily = Flex, fontWeight = weight ?: fontWeight)

/** Escala de Material 3 con la fuente expresiva y títulos con más peso. */
val ClimaTypography = Typography().run {
    copy(
        displayLarge = displayLarge.flex(),
        displayMedium = displayMedium.flex(),
        displaySmall = displaySmall.flex(),
        headlineLarge = headlineLarge.flex(FontWeight.Medium),
        headlineMedium = headlineMedium.flex(FontWeight.Medium),
        headlineSmall = headlineSmall.flex(FontWeight.Medium),
        titleLarge = titleLarge.flex(FontWeight.Medium),
        titleMedium = titleMedium.flex(FontWeight.SemiBold),
        titleSmall = titleSmall.flex(FontWeight.SemiBold),
        bodyLarge = bodyLarge.flex(),
        bodyMedium = bodyMedium.flex(),
        bodySmall = bodySmall.flex(),
        labelLarge = labelLarge.flex(FontWeight.SemiBold),
        labelMedium = labelMedium.flex(FontWeight.Medium),
        labelSmall = labelSmall.flex(FontWeight.Medium),
    )
}
