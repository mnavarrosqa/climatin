package com.mnavarrosqa.clima.wallpaper

import android.app.WallpaperColors
import android.os.Build
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.toArgb
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.ui.SkyPainter
import com.mnavarrosqa.clima.ui.SKY_CYCLE_MS
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.ui.toGradient

/**
 * Fondo de pantalla animado con el cielo del clima de donde estás. Dibuja con la GPU a la
 * frecuencia de la pantalla solo mientras se ve, y relee el clima una vez por minuto.
 */
class SkyWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SkyEngine()

    private inner class SkyEngine : Engine(), Choreographer.FrameCallback {
        private val choreographer = Choreographer.getInstance()
        private val painter = SkyPainter()
        private var visible = false
        private var scheduled = false
        private var kind = WeatherKind.PARTLY
        private var isDay = true
        private var loadedAt = 0L
        private var lastFrameNanos = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadWeather()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) schedule() else stop()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            stop()
            super.onDestroy()
        }

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors {
            // Le dice al sistema qué colores tiene el fondo para armar la paleta de Material You.
            val colors = skyColors(kind, isDay)
            return WallpaperColors(
                android.graphics.Color.valueOf(colors[0].toArgb()),
                android.graphics.Color.valueOf(colors[1].toArgb()),
                android.graphics.Color.valueOf(colors[2].toArgb()),
            )
        }

        private fun schedule() {
            if (scheduled) return
            scheduled = true
            choreographer.postFrameCallback(this)
        }

        private fun stop() {
            scheduled = false
            choreographer.removeFrameCallback(this)
        }

        private fun loadWeather() {
            loadedAt = SystemClock.uptimeMillis()
            val current = WeatherRepository.get(applicationContext).cached()?.current ?: return
            val newKind = WeatherCodes.kind(current.code)
            if (newKind != kind || current.isDay != isDay) {
                kind = newKind
                isDay = current.isDay
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
            }
        }

        /**
         * Un cuadro por refresco de pantalla, con Choreographer: así va sincronizado con el
         * sistema (sin saltos), y el canvas de hardware lo dibuja la GPU en vez del procesador.
         */
        override fun doFrame(frameTimeNanos: Long) {
            scheduled = false
            if (!visible) return
            // En pantallas de 90 o 120 Hz se dibuja a 60 cuadros: igual de fluido, menos batería.
            if (frameTimeNanos - lastFrameNanos < 14_000_000L) {
                schedule()
                return
            }
            lastFrameNanos = frameTimeNanos
            if (SystemClock.uptimeMillis() - loadedAt > 60_000) loadWeather()
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull()
            if (canvas != null) {
                try {
                    val clock = ((frameTimeNanos / 1_000_000L) % SKY_CYCLE_MS) / SKY_CYCLE_MS.toFloat()
                    painter.draw(
                        canvas,
                        canvas.width.toFloat(),
                        canvas.height.toFloat(),
                        resources.displayMetrics.density,
                        clock,
                        kind,
                        isDay,
                        skyColors(kind, isDay).toGradient(),
                    )
                } finally {
                    runCatching { holder.unlockCanvasAndPost(canvas) }
                }
            }
            schedule()
        }
    }
}
