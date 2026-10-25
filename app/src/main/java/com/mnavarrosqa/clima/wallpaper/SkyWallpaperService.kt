package com.mnavarrosqa.clima.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.mnavarrosqa.clima.CrashLog
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.WeatherSnapshot
import com.mnavarrosqa.clima.data.degrees
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.ui.SKY_CYCLE_MS
import com.mnavarrosqa.clima.ui.SkyTime
import com.mnavarrosqa.clima.ui.skyColors
import com.mnavarrosqa.clima.ui.toGradient

/**
 * Fondo de pantalla animado con el cielo del clima. Dibuja con la GPU solo mientras se ve, a 60
 * cuadros (30 en modo ahorro o con el ahorro de batería del sistema), o un cuadro por minuto en
 * modo quieto. Se corre al pasar de pantalla de inicio.
 */
class SkyWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SkyEngine()

    private inner class SkyEngine : Engine(), Choreographer.FrameCallback, SharedPreferences.OnSharedPreferenceChangeListener {
        private val choreographer = Choreographer.getInstance()
        private val handler = Handler(Looper.getMainLooper())
        private val painter = WallpaperPainter()
        private val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        private var settings = WallpaperPrefs.load(applicationContext)
        private var scene = wallpaperScene(null, settings)
        private var visible = false
        private var scheduled = false
        private var loadedAt = 0L
        private var lastFrameNanos = 0L
        private var parallax = 0.5f
        private var powerSave = false
        private var powerCheckedAt = 0L

        /** En modo quieto: un cuadro por minuto para seguir la hora y el clima. */
        private val minuteTick = object : Runnable {
            override fun run() {
                if (!visible) return
                loadWeather()
                schedule()
                handler.postDelayed(this, 60_000)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            WallpaperPrefs.prefs(applicationContext).registerOnSharedPreferenceChangeListener(this)
            // El fondo no reacciona a los toques.
            setTouchEventsEnabled(false)
            loadWeather()
        }

        /** Guardar los ajustes cambia varias claves juntas: se recarga una sola vez, al final. */
        private val reload = Runnable {
            settings = WallpaperPrefs.load(applicationContext)
            loadWeather()
            restart()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
            handler.removeCallbacks(reload)
            handler.post(reload)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                if (SystemClock.uptimeMillis() - loadedAt > 60_000) loadWeather()
                restart()
            } else {
                stop()
            }
        }

        override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xStep: Float, yStep: Float, xPixels: Int, yPixels: Int) {
            // Algunos launchers mandan siempre 0.5 o nada: entonces el fondo queda centrado.
            parallax = xOffset.coerceIn(0f, 1f)
            if (settings.motion == WallpaperMotion.STILL) schedule()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            drawFrame(System.nanoTime())
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            stop()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            stop()
            WallpaperPrefs.prefs(applicationContext).unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }

        @RequiresApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors {
            // Le dice al sistema qué colores tiene el fondo para armar la paleta de Material You.
            // Los colores base del clima, sin el tinte de la hora: si no, al amanecer y al atardecer
            // el sistema rearmaba la paleta de Material You cada minuto.
            val colors = skyColors(scene.kind, scene.isDay).toGradient()
            return WallpaperColors(
                android.graphics.Color.valueOf(colors.middle),
                android.graphics.Color.valueOf(colors.top),
                android.graphics.Color.valueOf(colors.bottom),
            )
        }

        private fun restart() {
            stop()
            if (!visible) return
            schedule()
            if (settings.motion == WallpaperMotion.STILL) handler.postDelayed(minuteTick, 60_000)
        }

        private fun schedule() {
            if (scheduled) return
            scheduled = true
            choreographer.postFrameCallback(this)
        }

        private fun stop() {
            scheduled = false
            choreographer.removeFrameCallback(this)
            handler.removeCallbacks(minuteTick)
            handler.removeCallbacks(reload)
        }

        private fun loadWeather() {
            loadedAt = SystemClock.uptimeMillis()
            val repository = WeatherRepository.get(applicationContext)
            val snapshot = runCatching { repository.cached(settings.placeId) ?: repository.cached() }.getOrNull()
            val updated = runCatching { wallpaperScene(snapshot, settings) }
                .onFailure { CrashLog.record(applicationContext, it, "fondo de pantalla") }
                .getOrElse { wallpaperScene(null, settings) }
            val colorsChanged = updated.kind != scene.kind || updated.isDay != scene.isDay
            scene = updated
            if (colorsChanged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
        }

        /** Tiempo mínimo entre cuadros según el modo: 60 o 30 por segundo. */
        private fun frameGapNanos(): Long {
            // El ahorro de batería se consulta cada 10 s, no en cada cuadro (en Android viejos es una llamada al sistema).
            val now = SystemClock.uptimeMillis()
            if (now - powerCheckedAt > 10_000) {
                powerCheckedAt = now
                powerSave = power.isPowerSaveMode
            }
            val saver = settings.motion == WallpaperMotion.SAVER || powerSave
            return if (saver) 30_000_000L else 14_000_000L
        }

        override fun doFrame(frameTimeNanos: Long) {
            scheduled = false
            if (!visible) return
            val now = SystemClock.uptimeMillis()
            val animating = settings.motion != WallpaperMotion.STILL
            val gap = frameGapNanos()
            if (animating && frameTimeNanos - lastFrameNanos < gap) {
                // Se pide el cuadro para cuando toca, no en cada refresco de pantalla (en una de 120 Hz
                // en modo ahorro eran 120 despertares por segundo para dibujar 30).
                scheduled = true
                choreographer.postFrameCallbackDelayed(this, ((gap - (frameTimeNanos - lastFrameNanos)) / 1_000_000L).coerceAtLeast(1L))
                return
            }
            lastFrameNanos = frameTimeNanos
            if (now - loadedAt > 60_000) loadWeather()
            drawFrame(frameTimeNanos)
            if (animating) schedule()
        }

        private fun drawFrame(frameTimeNanos: Long) {
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull()
                ?: runCatching { holder.lockCanvas() }.getOrNull()
                ?: return
            try {
                val clock = ((frameTimeNanos / 1_000_000L) % SKY_CYCLE_MS) / SKY_CYCLE_MS.toFloat()
                // Un error al dibujar un cuadro no puede tirar abajo el fondo de pantalla: se anota
                // y se sigue con el próximo.
                runCatching {
                painter.draw(
                    canvas,
                    canvas.width.toFloat(),
                    canvas.height.toFloat(),
                    resources.displayMetrics.density,
                    clock,
                    scene,
                    settings.style,
                    parallax,
                )
                }.onFailure { CrashLog.record(applicationContext, it, "fondo de pantalla") }
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
            }
        }
    }
}

/** Arma lo que hay que dibujar a partir del pronóstico guardado y los ajustes. */
fun wallpaperScene(snapshot: WeatherSnapshot?, settings: WallpaperSettings, fallback: Pair<WeatherKind, Boolean>? = null): WallpaperScene {
    val current = snapshot?.current
    val kind = fallback?.first ?: current?.code?.let(WeatherCodes::kind) ?: WeatherKind.PARTLY
    val isDay = fallback?.second ?: current?.isDay ?: true
    val colors = if (settings.timeOfDay && fallback == null) {
        SkyTime.gradient(kind, isDay, snapshot)
    } else {
        skyColors(kind, isDay).toGradient()
    }
    val showWeather = settings.showWeather && current != null
    return WallpaperScene(
        kind = kind,
        isDay = isDay,
        colors = colors,
        southern = (snapshot?.latitude ?: 0.0) < 0,
        temperature = current?.temperature?.degrees()?.takeIf { showWeather },
        caption = if (showWeather) {
            listOfNotNull(snapshot?.place ?: tr("Mi ubicación", "My location"), current?.code?.let(WeatherCodes::description))
                .joinToString(" · ")
        } else {
            null
        },
    )
}
