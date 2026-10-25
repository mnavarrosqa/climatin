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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
 * ubicación, los avisos y los gestos y widgets. Cada permiso se puede saltear; se vuelve a ver
 * desde Ajustes → Acerca de.
 */
@Composable
fun WelcomeScreen(
    viewModel: WeatherViewModel,
    onAllowLocation: () -> Unit,
    onFinish: () -> Unit,
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
                                Button(onClick = onAllowLocation) { Text(tr("Permitir ubicación", "Allow location")) }
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
                                Button(onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                    Text(tr("Activar avisos", "Turn on alerts"))
                                }
                            } else {
                                Button(onClick = {
                                    viewModel.setRainAlerts(true)
                                    viewModel.setSmnAlerts(true)
                                    canNotify = true
                                    next()
                                }) { Text(tr("Activar avisos", "Turn on alerts")) }
                            }
                        },
                    )
                    else -> Step(
                        visual = { WeatherIconBadge(0, isDay = false) },
                        title = tr("Un par de trucos", "A few tips"),
                        text = tr(
                            "Deslizá de costado para pasar de ciudad. En Ciudades, deslizá una a la izquierda para quitarla o a la derecha para ponerle un apodo, y mantenela apretada para moverla. Tocá cualquier detalle para saber qué significa.",
                            "Swipe sideways to switch cities. In Cities, swipe left to remove one or right to give it a nickname, and press and hold to move it. Tap any detail to learn what it means.",
                        ),
                        action = {
                            FilledTonalButton(onClick = { context.startActivity(Intent(context, WidgetGalleryActivity::class.java)) }) {
                                Text(tr("Ver los widgets", "See the widgets"))
                            }
                        },
                    )
                }
            }
            // Puntos del paso actual y el botón para seguir.
            Row(
                Modifier
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
                Button(onClick = next) {
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
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        visual()
        Spacer(Modifier.height(28.dp))
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
            color = OnSkyVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
private fun Done(label: String) {
    Text("✓ $label", style = MaterialTheme.typography.titleMedium, color = OnSky)
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
