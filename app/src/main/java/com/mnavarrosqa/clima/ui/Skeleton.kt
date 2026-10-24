package com.mnavarrosqa.clima.ui

import com.mnavarrosqa.clima.data.tr
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Lo que se ve mientras llega el primer pronóstico: la forma de la pantalla en gris, con un
 * brillo que la recorre. Así se nota que está cargando y no salta todo cuando aparecen los datos.
 */
@Composable
fun ForecastSkeleton() {
    // Se pasa como lambda y se lee al dibujar: el brillo no recompone la pantalla en cada cuadro.
    val shimmerState = rememberInfiniteTransition(label = "brillo").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "avance",
    )
    val shimmer = { shimmerState.value }
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { contentDescription = tr("Buscando el clima…", "Getting the weather…") },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Bone(shimmer, Modifier.width(160.dp).height(24.dp))
            Bone(shimmer, Modifier.width(120.dp).height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Bone(shimmer, Modifier.width(150.dp).height(96.dp), RoundedCornerShape(28.dp))
                Bone(shimmer, Modifier.size(96.dp), CircleShape)
            }
            Bone(shimmer, Modifier.width(200.dp).height(16.dp))
        }
        Bone(shimmer, Modifier.fillMaxWidth().height(36.dp), CircleShape)
        Bone(shimmer, Modifier.fillMaxWidth().height(170.dp), RoundedCornerShape(28.dp))
        Bone(shimmer, Modifier.fillMaxWidth().height(280.dp), RoundedCornerShape(28.dp))
    }
}

@Composable
private fun Bone(shimmer: () -> Float, modifier: Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    Box(
        modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f))
            .drawBehind {
                // El brillo avanza en diagonal y cruza cada hueso al mismo ritmo.
                val band = size.width.coerceAtLeast(240.dp.toPx())
                val x = shimmer() * band
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent),
                        start = Offset(x - band / 2, 0f),
                        end = Offset(x + band / 2, size.height),
                    ),
                )
            },
    )
}

