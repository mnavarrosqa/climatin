package com.mnavarrosqa.clima.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.mnavarrosqa.clima.R

/** Tamaños para probar en la vista previa: chico, mediano y grande. */
data class WidgetSizes(val small: DpSize, val medium: DpSize, val large: DpSize) {
    val all get() = listOf(small, medium, large)
}

/**
 * Un tipo de widget de Climatin: su receptor, cómo dibujarlo y cómo se llama. [parts], [supportsCity]
 * y [supportsTap] dicen qué opciones de la personalización le sirven, para no ofrecer las que no usa.
 */
class WidgetKind(
    val receiver: Class<out GlanceAppWidgetReceiver>,
    val nameRes: Int,
    val descriptionRes: Int,
    val sizes: WidgetSizes,
    val defaultStyle: WidgetStyle = WidgetStyle.SYSTEM,
    val hasMetric: Boolean = false,
    /** Partes que el widget muestra y se pueden ocultar. */
    val parts: Set<WidgetPart> = emptySet(),
    /** Muestra una sola ciudad elegible (Ciudades muestra todas). */
    val supportsCity: Boolean = true,
    /** Tocar el fondo hace lo elegido en "Al tocar el widget". */
    val supportsTap: Boolean = true,
    private val create: () -> GlanceAppWidget,
) {
    fun widget(): PreviewableWidget = create() as PreviewableWidget
    fun component(context: Context) = ComponentName(context, receiver)

    /** Widgets de este tipo que ya están en la pantalla de inicio. */
    fun placedIds(context: Context): List<Int> =
        runCatching { AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).toList() }.getOrDefault(emptyList())
}

private fun sizes(sw: Int, sh: Int, mw: Int, mh: Int, lw: Int, lh: Int) =
    WidgetSizes(DpSize(sw.dp, sh.dp), DpSize(mw.dp, mh.dp), DpSize(lw.dp, lh.dp))

object WidgetCatalog {
    /** Lo que agrega PlaceLabel (RefreshAction.kt): nombre de la ciudad, ⟳ y ›. */
    private val label = setOf(WidgetPart.PLACE, WidgetPart.REFRESH, WidgetPart.NEXT)

    val kinds: List<WidgetKind> = listOf(
        WidgetKind(
            ClimaWidgetReceiver::class.java, R.string.widget_clima_name, R.string.widget_clima_description, sizes(110, 110, 260, 120, 260, 260),
            // Por defecto, el cielo del clima de fondo, como los widgets del Clima de Apple.
            defaultStyle = WidgetStyle.SKY,
            parts = label + WidgetPart.RANGE + WidgetPart.INSIGHT,
        ) { ClimaWidget() },
        WidgetKind(
            PronosticoWidgetReceiver::class.java, R.string.widget_pronostico_name, R.string.widget_pronostico_description, sizes(200, 120, 260, 200, 300, 300),
            // Por defecto, el cielo del clima de fondo, como los widgets del Clima de Apple.
            defaultStyle = WidgetStyle.SKY,
            parts = label + WidgetPart.INSIGHT,
        ) { PronosticoWidget() },
        WidgetKind(
            VistazoWidgetReceiver::class.java, R.string.widget_vistazo_name, R.string.widget_vistazo_description, sizes(220, 70, 300, 90, 340, 110), WidgetStyle.TRANSPARENT,
            parts = setOf(WidgetPart.PLACE, WidgetPart.INSIGHT),
        ) { VistazoWidget() },
        WidgetKind(
            GraficoWidgetReceiver::class.java, R.string.widget_grafico_name, R.string.widget_grafico_description, sizes(250, 120, 300, 170, 330, 230),
            parts = label + WidgetPart.RANGE,
        ) { GraficoWidget() },
        WidgetKind(
            BurbujaWidgetReceiver::class.java, R.string.widget_burbuja_name, R.string.widget_burbuja_description, sizes(80, 80, 120, 120, 160, 160), WidgetStyle.COLOR,
            parts = setOf(WidgetPart.RANGE),
        ) { BurbujaWidget() },
        WidgetKind(
            PildoraWidgetReceiver::class.java, R.string.widget_pildora_name, R.string.widget_pildora_description, sizes(170, 60, 230, 70, 300, 84),
            parts = setOf(WidgetPart.RANGE),
        ) { PildoraWidget() },
        WidgetKind(
            SemanaWidgetReceiver::class.java, R.string.widget_semana_name, R.string.widget_semana_description, sizes(250, 130, 300, 200, 320, 280),
            // Por defecto, el cielo del clima de fondo, como los widgets del Clima de Apple.
            defaultStyle = WidgetStyle.SKY,
            parts = label,
        ) { SemanaWidget() },
        WidgetKind(SolWidgetReceiver::class.java, R.string.widget_sol_name, R.string.widget_sol_description, sizes(120, 120, 230, 120, 260, 190)) { SolWidget() },
        WidgetKind(
            ProximaHoraWidgetReceiver::class.java, R.string.widget_proxima_hora_name, R.string.widget_proxima_hora_description, sizes(190, 120, 260, 120, 300, 170),
            parts = label,
        ) { ProximaHoraWidget() },
        // Muestra todas las ciudades: no se elige una, y cada fila abre la suya.
        WidgetKind(
            CiudadesWidgetReceiver::class.java, R.string.widget_ciudades_name, R.string.widget_ciudades_description, sizes(190, 120, 260, 210, 300, 300),
            parts = setOf(WidgetPart.RANGE),
            supportsCity = false,
        ) { CiudadesWidget() },
        WidgetKind(
            IndicadorWidgetReceiver::class.java, R.string.widget_indicador_name, R.string.widget_indicador_description, sizes(80, 80, 120, 120, 160, 160),
            hasMetric = true,
        ) { IndicadorWidget() },
    )

    /** El tipo de un widget ya puesto, por su id. */
    fun kindOf(context: Context, appWidgetId: Int): WidgetKind? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className ?: return null
        return kinds.firstOrNull { it.receiver.name == provider }
    }

    /** Todos los widgets de Climatin puestos, con su tipo. */
    fun placed(context: Context): List<Pair<WidgetKind, Int>> =
        kinds.flatMap { kind -> kind.placedIds(context).map { kind to it } }
}
