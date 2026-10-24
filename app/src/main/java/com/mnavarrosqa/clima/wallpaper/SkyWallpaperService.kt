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
import com.mnavarrosqa.clima.ui.skyColors

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
            val (top, bottom) = skyColors(kind, isDay)
            return WallpaperColors(
                android.graphics.Color.valueOf(top.toArgb()),
                android.graphics.Color.valueOf(bottom.toArgb()),
                null,
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
                    val (top, bottom) = skyColors(kind, isDay)
                    val clock = ((SystemClock.uptimeMillis() - start) % 20_000L) / 20_000f
                    painter.draw(
                        canvas,
                        canvas.width.toFloat(),
                        canvas.height.toFloat(),
                        resources.displayMetrics.density,
                        clock,
                        kind,
                        isDay,
                        top.toArgb(),
                        bottom.toArgb(),
                    )
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
            handler.removeCallbacks(drawRunnable)
            if (visible) handler.postDelayed(drawRunnable, 50)
        }
    }
}
