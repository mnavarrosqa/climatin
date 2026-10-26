package com.mnavarrosqa.clima.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.mnavarrosqa.clima.data.Moon
import com.mnavarrosqa.clima.data.WeatherKind
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dibuja el cielo animado en un Canvas de Android. Lo usan la app (desde Compose) y el fondo de
 * pantalla animado.
 *
 * [clock] va de 0 a 1 en un minuto; cada elemento lo multiplica por un entero para que la
 * animación no salte al reiniciarse. Las nubes, la nieve, la niebla y los brillos son "puffs":
 * círculos con degradado radial que se desvanece, así los bordes quedan suaves.
 */
class SkyPainter {
    /**
     * Desde el hemisferio sur la luna se ve dada vuelta: la parte iluminada mientras crece queda
     * a la izquierda. Lo fija quien dibuja según la latitud del lugar.
     */
    var southern: Boolean = false


    private class Particle(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

    /** Un círculo de nube: posición y radio en anchos de nube; [top] es cuánto está en la cima (0 a 1). */
    private class Puff(val dx: Float, val dy: Float, val radius: Float, val top: Float)

    private fun particles(count: Int, seed: Int): List<Particle> {
        val random = Random(seed)
        return List(count) {
            Particle(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat())
        }
    }

    private val stars = particles(150, 1)
    private val farDrops = particles(90, 2)
    private val midDrops = particles(60, 6)
    private val nearDrops = particles(36, 5)
    private val flakes = particles(110, 3)
    private val clouds = particles(9, 4)

    /**
     * Cada nube son 16 círculos suaves dentro de una cúpula (base ancha y plana, cima redonda),
     * de abajo hacia arriba: superpuestos se ven como una nube con volumen y no como burbujas.
     */
    private val cloudShapes = clouds.indices.map { i ->
        val random = Random(10 + i)
        List(16) {
            val u = random.nextFloat() * 2 - 1
            val dome = kotlin.math.sqrt((1 - u * u).coerceAtLeast(0f))
            val v = random.nextFloat()
            Puff(dx = u * 0.42f, dy = -v * dome * 0.26f + 0.04f, radius = 0.12f + 0.1f * dome + random.nextFloat() * 0.06f, top = v * dome)
        }.sortedByDescending { it.dy }
    }

    /** El techo de nubes de los días cubiertos: círculos anchos y chatos a lo largo de arriba. */
    private val blanket = particles(16, 77)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val tau = 2 * PI.toFloat()

    /** El pincel de los puffs para dibujar en pantalla (las imágenes de nubes se arman con otro, en otro hilo). */
    private val brush = PuffBrush()

    private val rayShader = LinearGradient(0f, 0f, 1f, 0f, 0x29FFF5D2, 0x00FFF5D2, Shader.TileMode.CLAMP)
    private val rayPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(1f, -0.05f)
        lineTo(1f, 0.05f)
        close()
    }

    // El degradado del fondo se reusa mientras no cambien el alto ni los colores: antes se creaba
    // uno nuevo en cada cuadro, 60 veces por segundo.
    private var backgroundKey: Pair<Float, SkyGradient>? = null
    private var backgroundShaderCache: Shader? = null

    private fun backgroundShader(height: Float, colors: SkyGradient): Shader {
        val key = height to colors
        backgroundShaderCache?.takeIf { backgroundKey == key }?.let { return it }
        return LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(colors.top, colors.middle, colors.bottom),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        ).also {
            backgroundKey = key
            backgroundShaderCache = it
        }
    }

    private fun puff(canvas: Canvas, x: Float, y: Float, radiusX: Float, radiusY: Float, alpha: Float, rgb: Int, soft: Boolean = false) =
        brush.puff(canvas, x, y, radiusX, radiusY, alpha, rgb, soft)

    /**
     * Si se dibujan el sol y la luna. En el pronóstico van apagados: el ícono grande del estado
     * ya los muestra y se veían dos soles. Queda el resplandor, las estrellas y las nubes.
     */
    var showSunMoon = true

    /**
     * Si las nubes se guardan como imágenes. Para dibujar una sola vez (los widgets) no conviene:
     * se arman en otro hilo para los cuadros siguientes, que no van a llegar.
     */
    var useSprites = true

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        clock: Float,
        kind: WeatherKind,
        isDay: Boolean,
        colors: SkyGradient,
        background: Boolean = true,
        celestial: Boolean = true,
        cloudAlpha: Float = 1f,
        parallax: Float = 0f,
    ) {
        // Con tamaño 0 (al armar la pantalla o en una animación) los degradados redondos tiran error.
        if (width <= 0f || height <= 0f) return
        // Los fondos de pantalla "Formas" y "Oscuro" ponen su propio fondo y usan solo algunas capas.
        if (background) {
            // Opaco siempre: si una capa anterior dejó el pincel transparente (la luna lo hacía),
            // el cielo entero quedaba translúcido y se veía lo de atrás.
            paint.color = Color.BLACK
            paint.shader = backgroundShader(height, colors)
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
        }

        val clearish = kind == WeatherKind.CLEAR || kind == WeatherKind.PARTLY
        if (clearish && celestial) {
            if (isDay) drawSun(canvas, width, height, clock) else drawNight(canvas, width, height, density, clock)
        }
        if (kind != WeatherKind.CLEAR && cloudAlpha > 0f) drawClouds(canvas, width, height, clock, kind, isDay, cloudAlpha, parallax)
        when (kind) {
            WeatherKind.DRIZZLE -> {
                drawRain(canvas, width, height, density, clock, farDrops, 14f, 0.35f, 30, 0.8f, 0.08f)
                drawRain(canvas, width, height, density, clock, midDrops, 18f, 0.4f, 36, 1f, 0.08f)
            }
            WeatherKind.RAIN, WeatherKind.STORM -> {
                // Tres capas de gotas: las lejanas finas y cortas, las cercanas largas y marcadas.
                val slant = if (kind == WeatherKind.STORM) 0.18f else 0.1f
                drawRain(canvas, width, height, density, clock, farDrops, 16f, 0.3f, 40, 0.8f, slant)
                drawRain(canvas, width, height, density, clock, midDrops, 24f, 0.45f, 50, 1.1f, slant)
                drawRain(canvas, width, height, density, clock, nearDrops, 40f, 0.55f, 62, 1.6f, slant)
                // Bruma de la lluvia abajo.
                puff(canvas, width / 2, height, width * 0.9f, height * 0.25f, 0.18f, 0xC9D4E2)
            }
            WeatherKind.SNOW -> drawSnow(canvas, width, height, density, clock)
            WeatherKind.FOG -> drawFog(canvas, width, height, clock)
            else -> Unit
        }
        if (kind == WeatherKind.STORM) drawLightning(canvas, width, height, density, clock)
    }

    /**
     * Sol con un resplandor grande y cálido, rayos suaves que giran lento y destellos de lente
     * hacia el centro, como el cielo despejado del Clima de Apple. Sin [showSunMoon] queda solo
     * el resplandor.
     */
    private fun drawSun(canvas: Canvas, width: Float, height: Float, clock: Float) {
        val cx = width * 0.78f
        val cy = height * 0.09f
        val base = min(width, height)
        val breathe = 1f + 0.04f * sin(clock * tau * 3)
        puff(canvas, cx, cy, base * 1.3f * breathe, base * 1.1f * breathe, 0.28f, 0xFFE9B0)
        puff(canvas, cx, cy, base * 0.55f, base * 0.55f, 0.35f, 0xFFF4D6)
        if (!showSunMoon) return

        paint.shader = rayShader
        canvas.save()
        canvas.translate(cx, cy)
        // Un rayo de corrimiento por vuelta del reloj: al reiniciarse, los rayos quedan iguales.
        canvas.rotate(clock * 360f / 14)
        repeat(14) { i ->
            canvas.rotate(360f / 14)
            // Largos distintos: menos geométrico que doce rayos iguales.
            val length = base * (0.55f + 0.25f * ((i * 7) % 5) / 5f)
            canvas.save()
            canvas.scale(length, length)
            canvas.drawPath(rayPath, paint)
            canvas.restore()
        }
        canvas.restore()
        paint.shader = null

        puff(canvas, cx, cy, base * 0.2f, base * 0.2f, 0.9f, 0xFFF6DC)
        paint.color = Color.rgb(255, 252, 240)
        canvas.drawCircle(cx, cy, base * 0.07f, paint)
        paint.color = Color.BLACK
        // Destellos de lente: círculos tenues sobre la línea del sol hacia el centro.
        flares.forEach { (t, r, a, rgb) ->
            val x = cx + (width * 0.35f - cx) * t * 1.6f
            val y = cy + (height * 0.42f - cy) * t * 1.6f
            puff(canvas, x, y, base * r, base * r, a * (0.85f + 0.15f * sin(clock * tau * 2 + t * 5)), rgb)
        }
    }

    private data class Flare(val t: Float, val radius: Float, val alpha: Float, val rgb: Int)

    private val flares = listOf(
        Flare(0.35f, 0.035f, 0.12f, 0xFFFFFF),
        Flare(0.55f, 0.06f, 0.08f, 0xBFE3FF),
        Flare(0.72f, 0.022f, 0.14f, 0xFFE0B0),
        Flare(0.9f, 0.09f, 0.05f, 0xCDE8FF),
    )

    /** Estrellas que titilan (las más brillantes con una cruz), una fugaz por minuto y la luna con halo. */
    private fun drawNight(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float) {
        val unit = min(width, height * 0.75f)
        val tiles = kotlin.math.ceil(width / unit).toInt()
        for (tile in 0 until tiles) stars.forEach { s ->
            val speed = 2 + (s.speed * 4).toInt()
            val twinkle = 0.3f + 0.7f * (sin((clock * speed + s.phase) * tau) + 1f) / 2f
            val x = tile * unit + s.x * unit
            // Cada franja corrida en alto, así no se ve el mismo dibujo repetido.
            val y = ((s.y + tile * 0.41f) % 1f) * height * 0.7f
            paint.color = Color.argb((255 * 0.9f * twinkle * (0.35f + 0.65f * s.size)).toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, (0.5f + s.size * s.size * 1.6f) * density, paint)
            if (s.size > 0.93f) {
                val arm = 6f * density * twinkle
                linePaint.color = Color.argb((255 * 0.35f * twinkle).toInt(), 255, 255, 255)
                linePaint.strokeWidth = 0.8f * density
                canvas.drawLine(x - arm, y, x + arm, y, linePaint)
                canvas.drawLine(x, y - arm, x, y + arm, linePaint)
            }
        }

        val t = (clock - 0.42f) / 0.03f
        if (t in 0f..1f) {
            val x0 = width * 0.15f + t * width * 0.5f
            val y0 = height * 0.08f + t * height * 0.18f
            val x1 = x0 - 60f * density
            val y1 = y0 - 22f * density
            linePaint.shader = LinearGradient(
                x0, y0, x1, y1,
                Color.argb((230 * (1f - t)).toInt(), 255, 255, 255), Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            linePaint.strokeWidth = 1.6f * density
            canvas.drawLine(x0, y0, x1, y1, linePaint)
            linePaint.shader = null
        }

        if (!showSunMoon) return
        val mx = width * 0.76f
        val my = height * 0.11f
        val mr = min(width, height) * 0.07f
        puff(canvas, mx, my, mr * 6, mr * 6, 0.14f, 0xC8D7FF)
        puff(canvas, mx, my, mr * 2.2f, mr * 2.2f, 0.25f, 0xDCE6FF)
        drawMoon(canvas, mx, my, mr)
    }

    // La luna se arma una vez por tamaño y día: en cada cuadro solo se dibuja.
    private var moonCx = -1f
    private var moonCy = -1f
    private var moonSouthern = false
    private var moonDate: java.time.LocalDate? = null
    private var moonDateCheckedAt = 0L
    private val moonLit = Path()
    private var moonShade: Shader? = null
    private var moonAge = 0.5

    /**
     * La luna con su fase de hoy: la parte en sombra apenas se ve (luz cenicienta), la iluminada
     * tiene un sombreado suave hacia el borde y unos mares grises, como la de verdad.
     */
    private fun drawMoon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        // La fecha se mira una vez por minuto, no en cada cuadro.
        val nowMs = System.currentTimeMillis()
        if (nowMs - moonDateCheckedAt > 60_000L) {
            moonDateCheckedAt = nowMs
            val today = java.time.LocalDate.now()
            if (today != moonDate) {
                moonDate = today
                moonShade = null
            }
        }
        if (moonShade == null || cx != moonCx || cy != moonCy || southern != moonSouthern) {
            moonCx = cx
            moonCy = cy
            moonSouthern = southern
            moonAge = Moon.age(moonDate ?: java.time.LocalDate.now())
            val terminator = r * kotlin.math.abs(kotlin.math.cos(2 * PI * moonAge)).toFloat()
            // En el sur, la luz va del lado contrario.
            val waxing = (moonAge < 0.5) != southern
            val crescent = moonAge < 0.25 || moonAge > 0.75
            moonLit.reset()
            moonLit.moveTo(cx, cy - r)
            // Mitad iluminada: la derecha mientras crece, la izquierda mientras mengua.
            moonLit.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, if (waxing) 180f else -180f, false)
            val towardLit = if (waxing) -180f else 180f
            moonLit.arcTo(RectF(cx - terminator, cy - r, cx + terminator, cy + r), 90f, if (crescent) towardLit else -towardLit, false)
            moonLit.close()
            moonShade = RadialGradient(cx - r * 0.3f, cy - r * 0.3f, r * 1.4f, 0xFFFFFBEF.toInt(), 0xFFD9D4C4.toInt(), Shader.TileMode.CLAMP)
        }
        // Luz cenicienta: el disco entero, muy tenue.
        paint.color = Color.argb(46, 200, 214, 255)
        canvas.drawCircle(cx, cy, r, paint)
        // Casi luna nueva: no hay parte iluminada que dibujar.
        if (moonAge < 0.02 || moonAge > 0.98) {
            paint.color = Color.BLACK
            return
        }
        canvas.save()
        canvas.clipPath(moonLit)
        paint.shader = moonShade
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
        // Mares: manchas grises tenues dentro de la parte iluminada.
        paint.color = Color.argb(34, 90, 96, 110)
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.22f, r * 0.26f, paint)
        canvas.drawCircle(cx + r * 0.22f, cy + r * 0.05f, r * 0.2f, paint)
        canvas.drawCircle(cx - r * 0.05f, cy + r * 0.38f, r * 0.15f, paint)
        canvas.restore()
        paint.color = Color.BLACK
    }

    /** Colores de las nubes: cuerpo, base en sombra y brillo de la cima. */
    private fun cloudPalette(kind: WeatherKind, isDay: Boolean): IntArray = when (kind) {
        WeatherKind.STORM -> if (isDay) intArrayOf(0x6B7486, 0x2F3646, 0x98A3B6) else intArrayOf(0x2E3545, 0x12161F, 0x4A5468)
        WeatherKind.RAIN, WeatherKind.DRIZZLE -> if (isDay) intArrayOf(0x9AA6B6, 0x5A6577, 0xC9D2DE) else intArrayOf(0x2F3A4E, 0x161C28, 0x4B5872)
        WeatherKind.CLOUDY, WeatherKind.FOG -> if (isDay) intArrayOf(0xE3E9F0, 0x9AA8BA, 0xFFFFFF) else intArrayOf(0x39445C, 0x1D2436, 0x5A6886)
        WeatherKind.SNOW -> if (isDay) intArrayOf(0xEEF2F7, 0xAEBACB, 0xFFFFFF) else intArrayOf(0x3E4A62, 0x20283A, 0x63718F)
        else -> if (isDay) intArrayOf(0xFFFFFF, 0xB9CCE4, 0xFFFFFF) else intArrayOf(0x3A4766, 0x1C2440, 0x6D7FA8)
    }

    /**
     * Nubes en tres capas de profundidad (lejos chicas, tenues y lentas; cerca grandes y más
     * rápidas). Con cielo cubierto, lluvia, nieve o tormenta, además un techo de nubes arriba.
     */
    private fun drawClouds(canvas: Canvas, width: Float, height: Float, clock: Float, kind: WeatherKind, isDay: Boolean, strength: Float, parallax: Float) {
        val palette = cloudPalette(kind, isDay)
        // El tamaño de las nubes sale del lado chico: en tabletas apaisadas, medidas por el ancho,
        // quedaban enormes y borrosas.
        val unit = min(width, height * 0.75f)
        val count = when (kind) {
            WeatherKind.PARTLY -> 4
            WeatherKind.FOG -> 3
            WeatherKind.CLOUDY -> 9
            WeatherKind.STORM -> 8
            else -> 7
        }
        val heavy = kind != WeatherKind.PARTLY
        if (heavy && kind != WeatherKind.FOG) {
            // El techo se dibuja una vez en una imagen y se mece entero.
            val blanketShape = blanket
            val drawBlanket = { c: Canvas, b: PuffBrush ->
                blanketShape.forEachIndexed { i, p ->
                    val x = i / 15f * width * 1.2f + width * 0.05f
                    b.puff(c, x, height * 0.06f + p.y * height * 0.07f, width * (0.5f + p.size * 0.3f) * 0.5f, height * 0.16f, 0.85f * strength, palette[1])
                    b.puff(c, x, height * 0.03f + p.y * height * 0.06f, width * (0.5f + p.size * 0.3f) * 0.48f, height * 0.13f, 0.9f * strength, palette[0])
                }
            }
            val left = -width * 0.15f + sin(clock * tau) * width * 0.02f
            val top = -height * 0.1f
            val sprite = if (useSprites) Sprites.get("techo|${palette[0]}|$strength|$width|$height", width * 1.3f, height * 0.36f, drawBlanket) else null
            if (sprite != null) {
                drawSprite(canvas, sprite, left, top, width * 1.3f, height * 0.36f)
            } else {
                // Mientras se arma la imagen (en otro hilo), se dibuja directo: no hay salto.
                canvas.save()
                canvas.translate(left, top)
                drawBlanket(canvas, brush)
                canvas.restore()
            }
        }
        for (i in 0 until count) {
            val c = clouds[i]
            val layer = i % 3
            val cloudWidth = unit * LAYER_SCALE[layer] * (0.85f + 0.3f * c.size)
            val travel = width + cloudWidth * 1.4f
            // Vueltas enteras por ciclo: al reiniciarse el reloj, cada nube sigue donde estaba (con
            // velocidades fraccionarias saltaban cada minuto).
            val laps = if (layer == 2) 2 else 1
            // En el fondo de pantalla, al pasar de página las nubes cercanas se corren más que las
            // lejanas: da profundidad.
            val x = ((c.x + 0.15f * i + clock * laps) % 1f) * travel - cloudWidth * 0.7f -
                width * 0.05f * layer * parallax
            // Con cielo cubierto bajan más: llenan la parte de arriba de la pantalla.
            val spread = if (kind == WeatherKind.CLOUDY) 0.2f else 0.08f
            val y = height * ((if (heavy) 0.08f else 0.05f) + LAYER_TOP[layer] + spread * c.y)
            val alpha = strength * LAYER_ALPHA[layer] * if (kind == WeatherKind.PARTLY) 1f else 0.9f
            // Cada nube son 48 círculos con degradado: se dibuja una vez en una imagen y en cada
            // cuadro solo se mueve. Así el cielo va fluido aunque haya varias nubes.
            val spriteWidth = cloudWidth * 1.5f
            val spriteHeight = cloudWidth * 1.0f
            val shape = cloudShapes[i]
            val sprite = if (!useSprites) {
                null
            } else {
                Sprites.get("nube|$i|${cloudWidth.toInt()}|${palette[0]}|$alpha", spriteWidth, spriteHeight) { c, b ->
                    drawCloud(c, b, shape, spriteWidth / 2, cloudWidth * 0.6f, cloudWidth, alpha, palette)
                }
            }
            if (sprite != null) {
                drawSprite(canvas, sprite, x - spriteWidth / 2, y - cloudWidth * 0.6f, spriteWidth, spriteHeight)
            } else {
                drawCloud(canvas, brush, shape, x, y, cloudWidth, alpha, palette)
            }
        }
    }

    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val spriteRect = RectF()

    private fun drawSprite(canvas: Canvas, bitmap: android.graphics.Bitmap, x: Float, y: Float, width: Float, height: Float) {
        spriteRect.set(x, y, x + width, y + height)
        canvas.drawBitmap(bitmap, null, spriteRect, spritePaint)
    }

    /** Una nube con volumen: la base en sombra, el cuerpo y un brillo suave solo en la cima. */
    private fun drawCloud(canvas: Canvas, brush: PuffBrush, shape: List<Puff>, x: Float, y: Float, cloudWidth: Float, alpha: Float, palette: IntArray) {
        shape.forEach { p ->
            val r = p.radius * cloudWidth
            brush.puff(canvas, x + p.dx * cloudWidth, y + p.dy * cloudWidth + r * 0.25f, r * 1.05f, r * 0.8f, alpha * 0.7f, palette[1], soft = true)
        }
        shape.forEach { p ->
            val r = p.radius * cloudWidth
            brush.puff(canvas, x + p.dx * cloudWidth, y + p.dy * cloudWidth, r, r, alpha * 0.85f, palette[0], soft = true)
        }
        shape.forEach { p ->
            if (p.top < 0.45f) return@forEach
            val r = p.radius * cloudWidth * 0.8f
            brush.puff(canvas, x + p.dx * cloudWidth - r * 0.1f, y + p.dy * cloudWidth - r * 0.25f, r, r * 0.8f, alpha * 0.45f, palette[2], soft = true)
        }
    }

    /**
     * Gotas inclinadas en tres tramos de opacidad creciente: una estela que se desvanece sin
     * crear un degradado por gota en cada cuadro.
     */
    private fun drawRain(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        clock: Float,
        list: List<Particle>,
        length: Float,
        alpha: Float,
        falls: Int,
        strokeWidth: Float,
        slant: Float,
    ) {
        // En pantallas anchas las gotas se reparten en franjas del ancho de un teléfono: si no,
        // quedaban ralas.
        val unit = min(width, height * 0.75f)
        val tiles = kotlin.math.ceil(width / unit).toInt()
        for (tile in 0 until tiles) list.forEach { p ->
            val f = falls + (p.speed * falls * 0.5f).toInt()
            val len = length * density * (0.6f + 0.8f * p.size)
            val progress = (p.y + tile * 0.37f + clock * f) % 1f
            val y = progress * (height + len * 2) - len
            // Arrancan más a la derecha para que la inclinación no deje un hueco a un costado.
            val x = tile * unit + p.x * (unit + height * slant) - height * slant * progress
            val dx = -slant * len
            val strength = alpha * (0.6f + 0.4f * p.size)
            linePaint.strokeWidth = strokeWidth * density * (0.7f + 0.6f * p.size)
            for (k in 0 until 3) {
                linePaint.color = Color.argb((255 * strength * (k + 1) / 3f).toInt(), 220, 234, 255)
                canvas.drawLine(x + dx * k / 3f, y + len * k / 3f, x + dx * (k + 1) / 3f, y + len * (k + 1) / 3f, linePaint)
            }
        }
    }

    /** Nieve en tres capas: copos lejanos chicos y lentos, cercanos grandes, difusos y con más vaivén. */
    private fun drawSnow(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float) {
        val unit = min(width, height * 0.75f)
        val tiles = kotlin.math.ceil(width / unit).toInt()
        for (tile in 0 until tiles) flakes.forEach { f ->
            val layer = when {
                f.size < 0.45f -> 0
                f.size < 0.8f -> 1
                else -> 2
            }
            val y = ((f.y + tile * 0.37f + clock * (layer + 1)) % 1f) * (height + 40f) - 20f
            val sway = sin((clock * (3 + layer) + f.phase) * tau) * SNOW_SWAY[layer] * density
            val r = SNOW_SIZE[layer] * density * (0.8f + f.speed * 0.5f)
            puff(canvas, tile * unit + f.x * unit + sway, y, r * 1.6f, r * 1.6f, SNOW_ALPHA[layer], 0xFFFFFF, soft = layer == 2)
        }
    }

    private var hazeKey = -1f
    private var hazeShader: Shader? = null

    /** Bancos de niebla que se cruzan a distinta velocidad y una bruma más densa abajo. */
    private fun drawFog(canvas: Canvas, width: Float, height: Float, clock: Float) {
        repeat(7) { i ->
            val y = height * (0.2f + i * 0.11f)
            val dir = if (i % 2 == 0) -1f else 1f
            // Vueltas enteras por ciclo: si no, los bancos saltaban al reiniciarse el reloj.
            val raw = (clock * (1 + i % 2) * dir + i * 0.37f) % 1f
            val shift = (if (raw < 0) raw + 1f else raw) * width * 1.6f - width * 0.3f
            puff(canvas, shift, y, width * 0.9f, height * 0.07f, 0.26f, 0xFFFFFF)
            puff(canvas, shift - width * 1.6f * dir, y, width * 0.9f, height * 0.07f, 0.26f, 0xFFFFFF)
        }
        if (hazeKey != height || hazeShader == null) {
            hazeKey = height
            hazeShader = LinearGradient(0f, height * 0.5f, 0f, height, 0x00FFFFFF, 0x59FFFFFF, Shader.TileMode.CLAMP)
        }
        paint.shader = hazeShader
        canvas.drawRect(0f, height * 0.5f, width, height, paint)
        paint.shader = null
    }

    // El rayo se arma una vez por destello y tamaño: en cada cuadro solo se dibuja.
    private val boltPath = Path()
    private val branchPath = Path()
    private var boltKey = ""

    /**
     * Tormenta: relámpagos dobles con caída exponencial que iluminan las nubes por dentro, y en
     * dos de ellos el rayo con su resplandor y una rama.
     */
    private fun drawLightning(canvas: Canvas, width: Float, height: Float, density: Float, clock: Float) {
        var flash = 0f
        var index = -1
        FLASHES.forEachIndexed { i, start ->
            val t = clock - start
            if (t in 0f..0.02f) {
                val value = exp(-t * 250f)
                if (value > flash) {
                    flash = value
                    index = i
                }
            }
        }
        if (flash < 0.01f) return
        puff(canvas, width * 0.5f, height * 0.12f, width * 0.9f, height * 0.25f, flash * 0.5f, 0xDDE6FF)
        paint.color = Color.argb((255 * flash * 0.25f).toInt(), 225, 232, 255)
        canvas.drawRect(0f, 0f, width, height, paint)
        paint.color = Color.BLACK
        if (index == 1) return
        val key = "$index|$width|$height"
        if (key != boltKey) {
            boltKey = key
            val random = Random(index * 31 + 7)
            var x = width * (0.25f + random.nextFloat() * 0.5f)
            var y = height * 0.08f
            boltPath.reset()
            boltPath.moveTo(x, y)
            var middle: Pair<Float, Float>? = null
            var step = 0
            while (y < height * 0.62f) {
                y += height * (0.03f + random.nextFloat() * 0.04f)
                x += (random.nextFloat() - 0.5f) * width * 0.09f
                boltPath.lineTo(x, y)
                if (++step == 5) middle = x to y
            }
            branchPath.reset()
            middle?.let { (bx0, by0) ->
                var bx = bx0
                var by = by0
                branchPath.moveTo(bx, by)
                repeat(3) {
                    by += height * 0.03f
                    bx += width * 0.035f * if (random.nextBoolean()) 1f else -1f
                    branchPath.lineTo(bx, by)
                }
            }
        }
        val stroke = Paint.Style.STROKE
        linePaint.style = stroke
        linePaint.strokeJoin = Paint.Join.ROUND
        linePaint.color = Color.argb((255 * flash * 0.35f).toInt(), 190, 205, 255)
        linePaint.strokeWidth = 10f * density
        canvas.drawPath(boltPath, linePaint)
        linePaint.color = Color.argb((255 * flash).toInt(), 255, 255, 255)
        linePaint.strokeWidth = 2.2f * density
        canvas.drawPath(boltPath, linePaint)
        linePaint.color = Color.argb((255 * flash * 0.7f).toInt(), 255, 255, 255)
        linePaint.strokeWidth = 1.4f * density
        canvas.drawPath(branchPath, linePaint)
        linePaint.style = Paint.Style.FILL
    }

    private companion object {
        val LAYER_SCALE = floatArrayOf(0.42f, 0.62f, 0.9f)
        val LAYER_TOP = floatArrayOf(0.03f, 0.1f, 0.18f)
        val LAYER_ALPHA = floatArrayOf(0.55f, 0.8f, 0.95f)
        val SNOW_SIZE = floatArrayOf(1.4f, 2.4f, 4.2f)
        val SNOW_SWAY = floatArrayOf(6f, 10f, 16f)
        val SNOW_ALPHA = floatArrayOf(0.55f, 0.8f, 0.9f)
        val FLASHES = floatArrayOf(0.3f, 0.315f, 0.72f)

    }
}

/**
 * Dibuja "puffs" (círculos con degradado radial) con su propio pincel y sus degradados: así se
 * puede usar en otro hilo sin pisarse con el que dibuja en pantalla.
 */
internal class PuffBrush {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Degradados radiales de radio 1 por color; se escalan con el canvas en vez de crear uno por puff. */
    private val puffShaders = HashMap<Int, Shader>()
    private val softShaders = HashMap<Int, Shader>()

    private fun puffShader(rgb: Int): Shader = puffShaders.getOrPut(rgb) {
        val solid = rgb or 0xFF000000.toInt()
        val half = (rgb and 0x00FFFFFF) or 0x8C000000.toInt()
        val clear = rgb and 0x00FFFFFF
        RadialGradient(0f, 0f, 1f, intArrayOf(solid, half, clear), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    /** Más difuso hacia el borde: para nubes y copos cercanos, sin el canto marcado de un círculo. */
    private fun softShader(rgb: Int): Shader = softShaders.getOrPut(rgb) {
        val base = rgb and 0x00FFFFFF
        RadialGradient(
            0f, 0f, 1f,
            intArrayOf(base or 0xFF000000.toInt(), base or 0xBF000000.toInt(), base or 0x40000000, base),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun puff(canvas: Canvas, x: Float, y: Float, radiusX: Float, radiusY: Float, alpha: Float, rgb: Int, soft: Boolean = false) {
        if (alpha <= 0f || radiusX <= 0f || radiusY <= 0f) return
        paint.shader = if (soft) softShader(rgb) else puffShader(rgb)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(radiusX, radiusY)
        canvas.drawCircle(0f, 0f, 1f, paint)
        canvas.restore()
        paint.shader = null
        paint.alpha = 255
    }
}

/**
 * Imágenes de nubes ya dibujadas, compartidas entre todos los cielos (al pasar de ciudad se arma
 * uno nuevo y antes se redibujaban todas de golpe, con un tirón). Se arman en otro hilo: hasta que
 * están, quien dibuja las hace directo.
 */
private object Sprites {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount
    }
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nubes").apply { isDaemon = true }
    }

    fun get(key: String, width: Float, height: Float, draw: (Canvas, PuffBrush) -> Unit): android.graphics.Bitmap? {
        cache.get(key)?.let { return it }
        if (pending.add(key)) {
            worker.execute {
                runCatching {
                    val scale = min(SkyPainterScale.SCALE, SkyPainterScale.MAX_PX / maxOf(width, height))
                    val w = (width * scale).toInt().coerceAtLeast(1)
                    val h = (height * scale).toInt().coerceAtLeast(1)
                    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.scale(w / width, h / height)
                    draw(canvas, PuffBrush())
                    cache.put(key, bitmap)
                }
                pending.remove(key)
            }
        }
        return null
    }
}

/**
 * Resolución de las imágenes de nubes: más nítidas que antes (a 40% en tabletas se veían
 * borrosas), con un tope de unos 1100 px de lado para la memoria.
 */
private object SkyPainterScale {
    const val SCALE = 0.6f
    const val MAX_PX = 1100f
}

/** Tres colores del cielo de arriba hacia abajo. */
data class SkyGradient(val top: Int, val middle: Int, val bottom: Int)
