package com.mnavarrosqa.clima.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.mnavarrosqa.clima.data.tr

/** Pestañas de la barra de abajo. */
enum class NavTab { CLIMA, MAPA, CIUDADES, AJUSTES }

/**
 * Lo que tapa la barra flotante abajo (alto y margen): las listas lo suman a su relleno de abajo
 * para que lo último se pueda leer. Sin barra, 0.
 */
val LocalBottomBarSpace = compositionLocalOf { 0.dp }

/** Alto de la barra más su margen con el borde. */
val BottomBarSpace: Dp = 88.dp

/**
 * Barra de navegación flotante y translúcida, como una píldora sobre el contenido. Sobre el cielo
 * va como vidrio oscuro con íconos blancos (igual que las tarjetas); sobre Ciudades y Ajustes toma
 * los colores del tema. La pestaña elegida lleva su nombre y un fondo; las otras, solo el ícono.
 */
@Composable
fun FloatingNavBar(
    selected: NavTab,
    overSky: Boolean,
    onSelect: (NavTab) -> Unit,
    mapEnabled: Boolean,
    icon: @Composable (NavTab, Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container by animateColorAsState(
        if (overSky) Color.Black.copy(alpha = 0.30f) else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
        label = "barra fondo",
    )
    val content = if (overSky) Color.White else MaterialTheme.colorScheme.onSurface
    val indicator = if (overSky) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.secondaryContainer
    val border = if (overSky) Color.White.copy(alpha = 0.16f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Surface(
        modifier = modifier.widthIn(max = 440.dp).fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = container,
        border = BorderStroke(1.dp, border),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier
                .height(64.dp)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val enabled = tab != NavTab.MAPA || mapEnabled
                val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "pestaña")
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(selected = isSelected, enabled = enabled, role = Role.Tab, onClick = { onSelect(tab) })
                        .graphicsLayer { this.alpha = alpha },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) indicator else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon(tab, content) }
                        }
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = content.copy(alpha = if (isSelected) 1f else 0.8f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

val NavTab.label: String
    get() = when (this) {
        NavTab.CLIMA -> tr("Clima", "Weather")
        NavTab.MAPA -> tr("Mapa", "Map")
        NavTab.CIUDADES -> tr("Ciudades", "Cities")
        NavTab.AJUSTES -> tr("Ajustes", "Settings")
    }
