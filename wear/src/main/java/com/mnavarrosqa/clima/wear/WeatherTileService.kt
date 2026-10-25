package com.mnavarrosqa.clima.wear

import com.mnavarrosqa.clima.data.tr
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.Insights
import com.mnavarrosqa.clima.data.ageText
import com.mnavarrosqa.clima.core.R as CoreR

/** Tarjeta del reloj: ícono y temperatura, estado, máx/mín, la frase del momento y las próximas horas. */
class WeatherTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(30 * 60_000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(WearStore.cached(this))))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).apply {
                // Se mandan todos los íconos (son pocos): así la versión no depende del clima de ahora.
                WEATHER_ICONS.forEach { (id, drawable) ->
                    addIdToImageMapping(
                        id,
                        ResourceBuilders.ImageResource.Builder()
                            .setAndroidResourceByResId(
                                ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(drawable).build(),
                            )
                            .build(),
                    )
                }
            }.build(),
        )

    /** Ícono del clima (a todo color) por su id de recurso de la tarjeta. */
    private fun icon(code: Int, isDay: Boolean, size: Float): LayoutElementBuilders.LayoutElement {
        val drawable = WeatherCodes.icon(code, isDay)
        val id = WEATHER_ICONS.entries.firstOrNull { it.value == drawable }?.key ?: return spacer(size)
        return LayoutElementBuilders.Image.Builder()
            .setResourceId(id)
            .setWidth(dp(size))
            .setHeight(dp(size))
            .build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(1)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(
                        if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL,
                    )
                    .build(),
            )
            .build()

    private fun spacer(height: Float) =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(height)).build()

    private fun hSpacer(width: Float) =
        LayoutElementBuilders.Spacer.Builder().setWidth(dp(width)).build()

    /** Las próximas horas en columnas: hora, ícono y temperatura. */
    private fun hoursRow(snapshot: WeatherSnapshot): LayoutElementBuilders.LayoutElement? {
        val hours = snapshot.upcomingHours(5).drop(1)
        if (hours.isEmpty()) return null
        val row = LayoutElementBuilders.Row.Builder()
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        hours.forEachIndexed { index, hour ->
            if (index > 0) row.addContent(hSpacer(8f))
            row.addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(text(compactHour(hour.dateTime), 11f, SECONDARY))
                    .addContent(icon(hour.code, hour.isDay, 20f))
                    .addContent(text(hour.temperature.degrees(), 12f, PRIMARY))
                    .build(),
            )
        }
        return row.build()
    }

    private fun layout(snapshot: WeatherSnapshot?): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (snapshot == null) {
            column.addContent(text(tr("Abrí Climatin en el reloj", "Open Climatin on your watch"), 14f, PRIMARY))
        } else {
            val current = snapshot.withCurrentFromHours().current
            val today = snapshot.today()
            snapshot.place?.let { column.addContent(text(it, 12f, SECONDARY)) }
            column.addContent(
                LayoutElementBuilders.Row.Builder()
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(icon(current.code, current.isDay, 34f))
                    .addContent(hSpacer(6f))
                    .addContent(text(current.temperature.degrees(), 36f, PRIMARY))
                    .build(),
            )
            column.addContent(text(WeatherCodes.description(current.code), 14f, ACCENT, bold = true))
            if (today != null) {
                column.addContent(text(tr("Máx", "High") + " ${today.max.degrees()} · " + tr("Mín", "Low") + " ${today.min.degrees()}", 13f, SECONDARY))
            }
            // Datos viejos a la vista: una temperatura de hace horas no se confunde con la de ahora.
            // Si no, la frase del momento (lluvia que viene, alerta, UV…), como en el teléfono. Va
            // una sola de las dos: no entran ambas con las horas debajo.
            val note = if (System.currentTimeMillis() - snapshot.fetchedAt > 2 * 60 * 60_000L) {
                tr("Actualizado ", "Updated ") + ageText(snapshot.fetchedAt)
            } else {
                Insights.headline(snapshot)
            }
            note?.let {
                column.addContent(spacer(2f))
                column.addContent(text(it, 12f, ACCENT))
            }
            // Las próximas horas reemplazan a la lista de días, que está en la app.
            hoursRow(snapshot)?.let {
                column.addContent(spacer(6f))
                column.addContent(it)
            }
        }
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("abrir")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(MainActivity::class.java.name)
                            .build(),
                    )
                    .build(),
            )
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(column.build())
            .build()
    }

    companion object {
        // Cambiarla cada vez que cambien los íconos: el reloj guarda los recursos por versión.
        private const val RESOURCES_VERSION = "2"

        // Esta versión de protolayout no trae colores del tema del sistema: van fijos, por función.
        private const val PRIMARY = 0xFFFFFFFF.toInt()
        private const val SECONDARY = 0xFFC4C6CF.toInt()
        private const val ACCENT = 0xFFA4C9FF.toInt()

        /** Ids fijos (no el número de recurso, que cambia entre compilaciones) de cada ícono. */
        private val WEATHER_ICONS = mapOf(
            "clear_day" to CoreR.drawable.ic_weather_clear_day,
            "clear_night" to CoreR.drawable.ic_weather_clear_night,
            "partly_cloudy_day" to CoreR.drawable.ic_weather_partly_cloudy_day,
            "partly_cloudy_night" to CoreR.drawable.ic_weather_partly_cloudy_night,
            "cloudy" to CoreR.drawable.ic_weather_cloudy,
            "fog" to CoreR.drawable.ic_weather_fog,
            "drizzle" to CoreR.drawable.ic_weather_drizzle,
            "rain" to CoreR.drawable.ic_weather_rain,
            "snow" to CoreR.drawable.ic_weather_snow,
            "thunderstorm" to CoreR.drawable.ic_weather_thunderstorm,
        )
    }
}
