package com.mnavarrosqa.clima.wallpaper

import android.app.WallpaperColors
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
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
 * Fondo de pantalla animado con el cielo del clima de donde estás. Dibuja a unos 20 cuadros por
 * segundo solo mientras se ve, y relee el clima una vez por minuto.
 */
class SkyWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SkyEngine()

    private inner class SkyEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val painter = SkyPainter()
        private val start = SystemClock.uptimeMillis()
        private var visible = false
        private var kind = WeatherKind.PARTLY
        private var isDay = true
        private var loadedAt = 0L
        private val drawRunnable = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadWeather()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) drawFrame() else handler.removeCallbacks(drawRunnable)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunnable)
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

        private fun drawFrame() {
            if (SystemClock.uptimeMillis() - loadedAt > 60_000) loadWeather()
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockCanvas() }.getOrNull()
            if (canvas != null) {
                try {
                    val clock = ((SystemClock.uptimeMillis() - start) % SKY_CYCLE_MS) / SKY_CYCLE_MS.toFloat()
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
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            handler.removeCallbacks(drawRunnable)
            // Lluvia, nieve y tormenta se mueven rápido: 30 cuadros por segundo. El resto, con 15
            // alcanza para verse fluido y gasta la mitad de batería.
            val fast = kind == WeatherKind.RAIN || kind == WeatherKind.STORM ||
                kind == WeatherKind.SNOW || kind == WeatherKind.DRIZZLE
            if (visible) handler.postDelayed(drawRunnable, if (fast) 33L else 66L)
        }
    }
}
