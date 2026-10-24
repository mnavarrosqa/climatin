package com.mnavarrosqa.clima.data

import java.util.Locale

/**
 * Idioma de los textos. La app está escrita en español y trae inglés para el resto: si el
 * teléfono (o la app, desde Ajustes de Android 13+) está en español se usa español; si no, inglés.
 */
object Lang {
    val isSpanish: Boolean get() = Locale.getDefault().language == "es"

    /** Locale para fechas y nombres de días. */
    val locale: Locale get() = if (isSpanish) Locale.forLanguageTag("es-AR") else Locale.ENGLISH
}

/** Texto en español o en inglés según [Lang]. */
fun tr(es: String, en: String): String = if (Lang.isSpanish) es else en
