package com.mnavarrosqa.clima.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mnavarrosqa.clima.core.R as CoreR
import com.mnavarrosqa.clima.data.LocationProvider
import com.mnavarrosqa.clima.data.WeatherCodes
import com.mnavarrosqa.clima.data.WeatherKind
import com.mnavarrosqa.clima.data.tr
import com.mnavarrosqa.clima.widget.WidgetGalleryActivity
import com.mnavarrosqa.clima.work.RainNotifier
import kotlinx.coroutines.launch

/** Si ya se vio la bienvenida (se guarda al terminarla o saltearla). */
object Welcome {
    private const val SEEN = "bienvenida_vista"

    fun seen(context: Context): Boolean =
        context.getSharedPreferences("clima", Context.MODE_PRIVATE).getBoolean(SEEN, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences("clima", Context.MODE_PRIVATE).edit().putBoolean(SEEN, true).apply()
    }
}

/**
 * Bienvenida de la primera vez, en cuatro pasos sobre el cielo animado: qué es Climatin, la
 * ubicación, los avisos y las cuatro pestañas de la barra. Cada permiso se puede saltear; se
 * vuelve a ver desde Ajustes → Acerca de.
 */
@Composable
fun WelcomeScreen(
    viewModel: WeatherViewModel,
    onAllowLocation: () -> Unit,
    onFinish: () -> Unit,
    onPickCity: () -> Unit = {},
) {
    val context = LocalContext.current
    val pager = rememberPagerState { 4 }
    val scope = rememberCoroutineScope()
    var hasLocation by remember { mutableStateOf(LocationProvider.hasPermission(context)) }
    var canNotify by remember { mutableStateOf(RainNotifier.canNotify(context)) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        if (granted) {
            viewModel.setRainAlerts(true)
            viewModel.setSmnAlerts(true)
        }
    }
    // El permiso de ubicación lo pide la pantalla principal; al volver se relee.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        hasLocation = LocationProvider.hasPermission(context)
        canNotify = RainNotifier.canNotify(context)
    }
    val next: () -> Unit = {
        if (pager.currentPage < pager.pageCount - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onFinish()
    }
    // El cielo cambia de un paso al otro: de día despejado, nubes, lluvia y de noche.
    val skies = listOf(WeatherKind.CLEAR to true, WeatherKind.PARTLY to true, WeatherKind.RAIN to true, WeatherKind.CLEAR to false)
    val (kind, isDay) = skies[pager.currentPage]

    Box(Modifier.fillMaxSize()) {
        AnimatedSky(kind, isDay, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (pager.currentPage < pager.pageCount - 1) {
                    TextButton(onClick = onFinish) { Text(tr("Saltar", "Skip"), color = OnSky) }
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> Step(
                        visual = { AppIconBadge() },
                        title = tr("Hola, soy Climatin", "Hi, I'm Climatin"),
                        text = tr(
                            "El clima de donde estás y de tus ciudades, con avisos de lluvia, alertas del SMN, widgets y un cielo que se mueve con el clima real.",
                            "The weather where you are and in your cities, with rain alerts, official warnings, widgets and a sky that moves with the real weather.",
                        ),
                    )
                    1 -> Step(
                        visual = { WeatherIconBadge(2) },
                        title = tr("¿Dónde estás?", "Where are you?"),
                        text = tr(
                            "Con tu ubicación aproximada te muestro el clima de donde estés y los widgets te siguen. No sale de tu teléfono salvo para pedir el pronóstico. Si preferís, podés usar solo ciudades.",
                            "With your approximate location I show the weather wherever you are and widgets follow you. It only leaves your phone to get the forecast. You can also use cities only.",
                        ),
                        action = {
                            if (hasLocation) {
                                Done(tr("Ubicación permitida", "Location allowed"))
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(onClick = onAllowLocation, colors = skyButtonColors()) { Text(tr("Permitir ubicación", "Allow location")) }
                                    Spacer(Modifier.height(8.dp))
                                    // El texto ofrece usar solo ciudades: el botón lleva directo a buscarla.
                                    FilledTonalButton(onClick = onPickCity, colors = skyTonalButtonColors()) { Text(tr("Usar una ciudad", "Use a city")) }
                                }
                            }
                        },
                    )
                    2 -> Step(
                        visual = { WeatherIconBadge(63) },
                        title = tr("Te aviso antes de que llueva", "I'll warn you before it rains"),
                        text = tr(
                            "Cuándo empieza y para la lluvia, las alertas oficiales y, si querés, un resumen del día. Todo se configura en Ajustes → Notificaciones, con horario de silencio.",
                            "When rain starts and stops, official alerts and, if you want, a daily summary. Everything is in Settings → Notifications, with quiet hours.",
                        ),
                        action = {
                            if (canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Done(tr("Avisos activados", "Alerts on"))
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Button(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }, colors = skyButtonColors()) {
                                    Text(tr("Activar avisos", "Turn on alerts"))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.setRainAlerts(true)
                                        viewModel.setSmnAlerts(true)
                                        canNotify = true
                                        next()
                                    },
                                    colors = skyButtonColors(),
                                ) { Text(tr("Activar avisos", "Turn on alerts")) }
                            }
                        },
                    )
                    else -> Step(
                        visual = { WeatherIconBadge(0, isDay = false) },
                        title = tr("Todo en cuatro pestañas", "Everything in four tabs"),
                        text = tr(
                            "La barra te lleva de una a otra.",
                            "The bar takes you from one to the other.",
                        ),
                        action = {
                            Column(
                                Modifier.widthIn(max = 480.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                TabTip(
                                    icon = {
                                        Image(painterResource(WeatherCodes.icon(2)), contentDescription = null, modifier = Modifier.size(24.dp))
                                    },
                                    name = NavTab.CLIMA.label,
                                    text = tr(
                                        "Deslizá de costado para pasar de ciudad y tocá cualquier detalle para saber qué significa.",
                                        "Swipe sideways to switch cities and tap any detail to learn what it means.",
                                    ),
                                )
                                TabTip(
                                    icon = { Icon(MapTabIcon, contentDescription = null, tint = OnSky) },
                                    name = NavTab.MAPA.label,
                                    text = tr(
                                        "La lluvia sobre el mapa, hora por hora.",
                                        "Rain on the map, hour by hour.",
                                    ),
                                )
                                TabTip(
                                    icon = { Icon(CitiesTabIcon, contentDescription = null, tint = OnSky) },
                                    name = NavTab.CIUDADES.label,
                                    text = tr(
                                        "Buscá y guardá ciudades. Deslizá una para quitarla o ponerle un apodo, y mantenela apretada para moverla.",
                                        "Search and save cities. Swipe one to remove or nickname it, and press and hold to move it.",
                                    ),
                                )
                                TabTip(
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null, tint = OnSky) },
                                    name = NavTab.AJUSTES.label,
                                    text = tr(
                                        "Unidades, avisos, apariencia y widgets.",
                                        "Units, alerts, appearance and widgets.",
                                    ),
                                )
                                // Secundario: en este paso la acción principal es "Empezar".
                                TextButton(onClick = { context.startActivity(Intent(context, WidgetGalleryActivity::class.java)) }) {
                                    Text(tr("Ver los widgets", "See the widgets"), color = OnSky)
                                }
                            }
                        },
                    )
                }
            }
            // Puntos del paso actual y el botón para seguir.
            // En pantallas anchas no se estira: los puntos y el botón quedaban en bordes opuestos.
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .semantics { contentDescription = tr("Paso ${pager.currentPage + 1} de 4", "Step ${pager.currentPage + 1} of 4") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f)) {
                    repeat(pager.pageCount) { index ->
                        val selected = index == pager.currentPage
                        val width by animateDpAsState(if (selected) 22.dp else 8.dp, Motion.snappy(), label = "punto")
                        val alpha by animateFloatAsState(if (selected) 1f else 0.45f, label = "punto alfa")
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(width = width, height = 8.dp)
                                .clip(CircleShape)
                                .background(OnSky.copy(alpha = alpha)),
                        )
                    }
                }
                Button(onClick = next, colors = skyButtonColors()) {
                    Text(if (pager.currentPage == pager.pageCount - 1) tr("Empezar", "Get started") else tr("Siguiente", "Next"))
                }
            }
        }
    }
}

@Composable
private fun Step(visual: @Composable () -> Unit, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            // Apaisado o con letra grande no entra: se desplaza en vez de cortarse.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        visual()
        Spacer(Modifier.height(28.dp))
        // Título, texto y acción en el vidrio de las tarjetas: sobre el cielo de día despejado el
        // blanco suelto apenas se leía.
        SkyGlass(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium),
                    color = OnSky,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = SkySurface.contentVariant,
                    textAlign = TextAlign.Center,
                )
                if (action != null) {
                    Spacer(Modifier.height(24.dp))
                    action()
                }
            }
        }
    }
}

/**
 * Botón principal sobre el cielo o su vidrio: blanco con el tinte del vidrio como texto. El
 * primario del tema (con color dinámico, cualquiera) se perdía sobre el cielo.
 */
@Composable
internal fun skyButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(containerColor = SkySurface.content, contentColor = SkySurface.Tint)

/** Secundario sobre el cielo: blanco translúcido, como la marca de la barra, con texto blanco. */
@Composable
internal fun skyTonalButtonColors(): ButtonColors =
    ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = SkySurface.content)

@Composable
private fun Done(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = OnSky, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = OnSky)
    }
}

/** Una pestaña de la barra en el último paso: su ícono, su nombre y para qué sirve. */
@Composable
private fun TabTip(icon: @Composable () -> Unit, name: String, text: String) {
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = OnSky)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = OnSkyVariant)
        }
    }
}

/** El ícono de la app, grande. */
@Composable
private fun AppIconBadge() {
    Box(Modifier.size(112.dp).clip(CircleShape)) {
        Image(painterResource(CoreR.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(
            painterResource(CoreR.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.45f
                    scaleY = 1.45f
                },
        )
    }
}

@Composable
private fun WeatherIconBadge(code: Int, isDay: Boolean = true) {
    Image(
        painterResource(WeatherCodes.icon(code, isDay)),
        contentDescription = null,
        modifier = Modifier.size(120.dp),
    )
}
