package com.mnavarrosqa.clima.wallpaper

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.mnavarrosqa.clima.data.CURRENT_PLACE_ID
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.WeatherRepository
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.ui.SKY_CYCLE_MS
import com.mnavarrosqa.clima.ui.theme.ClimaTheme

/**
 * Ajustes del fondo de pantalla: diseño, ciudad, colores según la hora, temperatura, tacto y
 * movimiento, con una vista previa en vivo. La abre la app y también el selector de fondos del
 * sistema (botón de ajustes del fondo).
 */
class WallpaperSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClimaTheme { WallpaperSettingsScreen(onBack = ::finish) }
        }
    }
}

/** Climas para probar cómo queda el fondo, además del de ahora. */
private val TryKinds = listOf(
    WeatherKind.CLEAR to { tr("Despejado", "Clear") },
    WeatherKind.PARTLY to { tr("Algo nublado", "Partly cloudy") },
    WeatherKind.CLOUDY to { tr("Nublado", "Cloudy") },
    WeatherKind.RAIN to { tr("Lluvia", "Rain") },
    WeatherKind.STORM to { tr("Tormenta", "Storm") },
    WeatherKind.SNOW to { tr("Nieve", "Snow") },
    WeatherKind.FOG to { tr("Niebla", "Fog") },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { WeatherRepository.get(context) }
    val places = remember { repository.places() }
    var settings by remember { mutableStateOf(WallpaperPrefs.load(context)) }
    var active by remember { mutableStateOf(LiveWallpaper.isActive(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { active = LiveWallpaper.isActive(context) }
    // Clima de prueba (solo en la vista previa): null es el de verdad.
    var tryKind by remember { mutableStateOf<WeatherKind?>(null) }
    var tryNight by remember { mutableStateOf(false) }

    fun update(change: WallpaperSettings) {
        settings = change
        WallpaperPrefs.save(context, change)
    }

    val snapshot = remember(settings.placeId) { repository.cached(settings.placeId) ?: repository.cached() }
    val scene = wallpaperScene(snapshot, settings, tryKind?.let { it to !tryNight })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("Fondo de pantalla", "Wallpaper")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            WallpaperPreview(
                scene = scene,
                style = settings.style,
                touch = settings.touch,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(220.dp)
                    .aspectRatio(9f / 19.5f),
            )
            Text(
                tr("Deslizá de costado y tocá la vista previa", "Swipe sideways and tap the preview"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )

            Section(tr("Probar con otro clima", "Try another weather"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tryKind == null, onClick = { tryKind = null }, label = { Text(tr("El de ahora", "Current")) })
                TryKinds.forEach { (kind, label) ->
                    FilterChip(selected = tryKind == kind, onClick = { tryKind = kind }, label = { Text(label()) })
                }
            }
            if (tryKind != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilterChip(selected = !tryNight, onClick = { tryNight = false }, label = { Text(tr("De día", "Day")) })
                    FilterChip(selected = tryNight, onClick = { tryNight = true }, label = { Text(tr("De noche", "Night")) })
                }
            }

            Section(tr("Diseño", "Design"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WallpaperStyle.entries.forEach { style ->
                    StyleOption(
                        style = style,
                        scene = scene,
                        selected = settings.style == style,
                        onClick = { update(settings.copy(style = style)) },
                    )
                }
            }
            Text(
                settings.style.description(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Section(tr("Ciudad", "City"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.placeId == CURRENT_PLACE_ID,
                    onClick = { update(settings.copy(placeId = CURRENT_PLACE_ID)) },
                    label = { Text(tr("Donde estoy", "My location")) },
                )
                places.forEach { place ->
                    FilterChip(
                        selected = settings.placeId == place.id,
                        onClick = { update(settings.copy(placeId = place.id)) },
                        label = { Text(place.shownName) },
                    )
                }
            }

            Section(tr("Movimiento", "Motion"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallpaperMotion.entries.forEach { motion ->
                    FilterChip(
                        selected = settings.motion == motion,
                        onClick = { update(settings.copy(motion = motion)) },
                        label = { Text(motion.label()) },
                    )
                }
            }
            Text(
                when (settings.motion) {
                    WallpaperMotion.SMOOTH -> tr("60 cuadros por segundo; baja a 30 con el ahorro de batería.", "60 frames per second; drops to 30 in battery saver.")
                    WallpaperMotion.SAVER -> tr("30 cuadros por segundo: igual de lindo, menos batería.", "30 frames per second: just as nice, less battery.")
                    WallpaperMotion.STILL -> tr("Un cuadro por minuto: casi no gasta batería.", "One frame per minute: barely uses battery.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Section(tr("Opciones", "Options"))
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                Column {
                    SwitchRow(
                        tr("Colores según la hora", "Colors by time of day"),
                        tr("Rosados al amanecer y naranjas al atardecer", "Pink at sunrise and orange at sunset"),
                        settings.timeOfDay,
                    ) { update(settings.copy(timeOfDay = it)) }
                    SwitchRow(
                        tr("Mostrar la temperatura", "Show the temperature"),
                        tr("La temperatura y el lugar sobre el fondo", "Temperature and place on the wallpaper"),
                        settings.showWeather,
                    ) { update(settings.copy(showWeather = it)) }
                    SwitchRow(
                        tr("Responder al tacto", "Respond to touch"),
                        tr("Una onda donde tocás la pantalla de inicio", "A ripple where you tap the home screen"),
                        settings.touch,
                    ) { update(settings.copy(touch = it)) }
                }
            }

            Spacer(Modifier.height(24.dp))
            if (active) {
                Text(
                    tr("Ya es tu fondo de pantalla: los cambios se ven al instante.", "It's already your wallpaper: changes apply right away."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(onClick = { LiveWallpaper.open(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Poner como fondo de pantalla", "Set as wallpaper"))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

/** Miniatura de un diseño; la elegida lleva borde y se agranda un poco. */
@Composable
private fun StyleOption(style: WallpaperStyle, scene: WallpaperScene, selected: Boolean, onClick: () -> Unit) {
    val zoom by animateFloatAsState(if (selected) 1f else 0.94f, label = "diseño")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WallpaperPreview(
            scene = scene.copy(temperature = null, caption = null),
            style = style,
            touch = false,
            onTap = onClick,
            modifier = Modifier
                .width(84.dp)
                .aspectRatio(9f / 19.5f)
                .then(
                    if (selected) {
                        Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(18.dp))
                    } else {
                        Modifier
                    },
                )
                .semantics { contentDescription = style.label() },
            corner = 18.dp,
            zoom = zoom,
        )
        Text(
            style.label(),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * El fondo dibujado con el mismo pintor del servicio. Deslizar mueve el efecto de páginas y tocar
 * deja una onda (si [touch]); con [onTap], tocar elige en vez de dejar onda.
 */
@Composable
fun WallpaperPreview(
    scene: WallpaperScene,
    style: WallpaperStyle,
    touch: Boolean,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    corner: Dp = 32.dp,
    zoom: Float = 1f,
) {
    val painter = remember { WallpaperPainter() }
    val clock by rememberInfiniteTransition(label = "fondo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SKY_CYCLE_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "reloj",
    )
    var parallax by remember { mutableFloatStateOf(0.5f) }
    val ripples = remember { mutableStateListOf<Ripple>() }
    Box(
        modifier
            .scale(zoom)
            .clip(RoundedCornerShape(corner))
            .pointerInput(onTap, touch) {
                detectTapGestures { offset ->
                    if (onTap != null) {
                        onTap()
                    } else if (touch) {
                        ripples.add(Ripple(offset.x, offset.y, SystemClock.uptimeMillis()))
                        if (ripples.size > 6) ripples.removeAt(0)
                    }
                }
            }
            // Las miniaturas no se arrastran, así la fila de diseños se puede deslizar.
            .then(
                if (onTap == null) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { change, amount ->
                            change.consume()
                            parallax = (parallax - amount / size.width).coerceIn(0f, 1f)
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val now = SystemClock.uptimeMillis()
            // Leer el reloj hace que se redibuje en cada cuadro.
            val tick = clock
            drawIntoCanvas {
                painter.draw(
                    it.nativeCanvas,
                    size.width,
                    size.height,
                    density,
                    tick,
                    scene,
                    style,
                    parallax,
                    ripples.filter { r -> now - r.startMs <= RIPPLE_MS },
                    now,
                )
            }
        }
    }
}
