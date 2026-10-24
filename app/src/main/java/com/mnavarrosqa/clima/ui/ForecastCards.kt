package com.mnavarrosqa.clima.ui

import android.content.Context
import com.mnavarrosqa.clima.data.tr

/** Tarjetas del pronóstico que se pueden ordenar y ocultar, como en el clima del Pixel. */
enum class ForecastCard(private val es: String, private val en: String, val leftPane: Boolean) {
    HOURS("Próximas horas", "Next hours", leftPane = false),
    DAYS("Próximos días", "Next days", leftPane = false),
    RADAR("Mapa de lluvia", "Rain map", leftPane = true),
    DETAILS("Detalles", "Details", leftPane = true),
    ;

    val label: String get() = tr(es, en)
}

data class CardLayout(
    val order: List<ForecastCard> = ForecastCard.entries,
    val hidden: Set<ForecastCard> = emptySet(),
) {
    val visible: List<ForecastCard> get() = order.filter { it !in hidden }

    fun move(card: ForecastCard, by: Int): CardLayout {
        val from = order.indexOf(card)
        val to = (from + by).coerceIn(0, order.lastIndex)
        return copy(order = order.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun toggle(card: ForecastCard, show: Boolean) = copy(hidden = if (show) hidden - card else hidden + card)
}

object CardLayoutPrefs {
    private const val ORDER = "tarjetas_orden"
    private const val HIDDEN = "tarjetas_ocultas"

    private fun prefs(context: Context) = context.getSharedPreferences("clima", Context.MODE_PRIVATE)

    private fun parse(raw: String?): List<ForecastCard> =
        raw.orEmpty().split(',').mapNotNull { name -> ForecastCard.entries.firstOrNull { it.name == name } }

    fun load(context: Context): CardLayout {
        val p = prefs(context)
        // Si en una versión nueva aparece otra tarjeta, se agrega al final.
        val saved = parse(p.getString(ORDER, null)).distinct()
        val order = saved + ForecastCard.entries.filter { it !in saved }
        return CardLayout(order, parse(p.getString(HIDDEN, null)).toSet())
    }

    fun save(context: Context, layout: CardLayout) {
        prefs(context).edit()
            .putString(ORDER, layout.order.joinToString(",") { it.name })
            .putString(HIDDEN, layout.hidden.joinToString(",") { it.name })
            .apply()
    }
}
