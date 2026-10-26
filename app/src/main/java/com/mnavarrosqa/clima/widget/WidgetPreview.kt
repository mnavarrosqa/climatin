package com.mnavarrosqa.clima.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.mnavarrosqa.clima.CrashLog
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.ui.skyColors

/**
 * El widget de verdad: se arma con Glance igual que en la pantalla de inicio, con los ajustes que
 * se están eligiendo, y se muestra como vista. Si algo falla, queda la versión anterior.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun GlanceWidgetPreview(
    kind: WidgetKind,
    size: DpSize,
    config: WidgetConfig,
    modifier: Modifier = Modifier,
    /** Lo que se muestra si el widget no se puede armar acá: nunca queda el recuadro vacío. */
    fallback: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(kind, size, config) {
        val state = mutablePreferencesOf().also { config.writeTo(it) }
        runCatching {
            GlanceRemoteViews().compose(context, size, state) { kind.widget().Body() }.remoteViews
        }.onSuccess {
            views = it
            failed = false
        }.onFailure {
            // Queda anotado en "Último error" para poder ver qué pasó.
            CrashLog.recordCaught(context, it, "vista previa del widget")
            failed = views == null
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val remote = views
        if (remote == null || failed) {
            fallback()
        } else {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    frame.removeAllViews()
                    runCatching { frame.addView(remote.apply(frame.context, frame)) }
                        .onFailure {
                            CrashLog.recordCaught(frame.context, it, "vista previa del widget")
                            failed = true
                        }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Fondos de pantalla de prueba, para ver cómo queda cada estilo (sobre todo el transparente). */
enum class PreviewBackdrop(private val es: String, private val en: String) {
    LIGHT("Claro", "Light"),
    DARK("Oscuro", "Dark"),
    PHOTO("Foto", "Photo"),
    SKY("Cielo", "Sky"),
    ;

    val label: String get() = tr(es, en)
}

/** Dibuja el fondo de prueba: liso, oscuro, una "foto" de atardecer o el cielo de Climatin. */
fun Modifier.backdrop(backdrop: PreviewBackdrop): Modifier = when (backdrop) {
    PreviewBackdrop.LIGHT -> background(Brush.verticalGradient(listOf(Color(0xFFF1F3F8), Color(0xFFDDE3EE))))
    PreviewBackdrop.DARK -> background(Brush.verticalGradient(listOf(Color(0xFF15181E), Color(0xFF262B35))))
    PreviewBackdrop.SKY -> background(Brush.verticalGradient(skyColors(WeatherKind.PARTLY, true)))
    PreviewBackdrop.PHOTO -> drawBehind {
        // Un atardecer con montañas: colores fuertes y contraste, como una foto de fondo típica.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2C6B), Color(0xFFD9607A), Color(0xFFFFB067))))
        // Las montañas se miden con el alto: en un recuadro ancho, medidas con el ancho tapaban
        // todo el cielo y la "foto" quedaba violeta lisa.
        val hill = size.height
        drawCircle(Color(0xFFFFE3A3), radius = size.minDimension * 0.16f, center = Offset(size.width * 0.7f, size.height * 0.55f))
        drawCircle(Color(0xFF2B2350), radius = hill * 0.75f, center = Offset(size.width * 0.15f, size.height * 1.3f))
        drawCircle(Color(0xFF3B2E5E), radius = hill * 0.8f, center = Offset(size.width * 0.9f, size.height * 1.4f))
    }
}
