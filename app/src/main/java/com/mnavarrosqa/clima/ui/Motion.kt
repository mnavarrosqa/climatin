package com.mnavarrosqa.clima.ui

import android.os.SystemClock
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Resortes compartidos: suaves y sin rebote exagerado, al estilo Material 3. */
object Motion {
    fun <T> gentle() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow)
    fun <T> snappy() = spring<T>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)

    /**
     * Entre pestañas de la barra: un fundido corto, sin escala. Son pantallas hermanas; que una
     * "entre" agrandándose daba a entender que se abría algo encima.
     */
    fun tabSwitch(): ContentTransform =
        fadeIn(tween(150, delayMillis = 60)).togetherWith(fadeOut(tween(90)))

    /** Al abrir algo encima (comparar, la bienvenida): entra agrandándose y lo de atrás se apaga. */
    fun drillIn(): ContentTransform =
        (fadeIn(tween(320, delayMillis = 60)) + scaleIn(tween(380, easing = EaseOutCubic), initialScale = 0.94f))
            .togetherWith(fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 1.03f))
}

/**
 * Entrada escalonada: el elemento sube y aparece con un pequeño retraso según su posición.
 * Solo anima durante el primer segundo y medio desde [shownAt] (la primera vez que se ve el
 * pronóstico al abrir la app); lo que aparece después, al desplazar o al volver de otra pestaña,
 * ya está quieto.
 */
@Composable
fun Modifier.reveal(index: Int, shownAt: Long): Modifier {
    val animate = SystemClock.uptimeMillis() - shownAt < 1_500
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * 55L)
            progress.animateTo(1f, Motion.gentle())
        }
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 32.dp.toPx()
    }
}

/** Clic con un leve hundimiento al apretar, además del ripple. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Motion.snappy(), label = "presión")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = source, indication = LocalIndication.current, onClick = onClick)
}
