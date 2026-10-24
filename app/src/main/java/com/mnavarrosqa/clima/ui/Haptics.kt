package com.mnavarrosqa.clima.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Vibraciones con forma propia, armadas con primitivas del motor háptico (Android 11+): se
 * sienten como golpecitos nítidos en vez de un zumbido. Si el teléfono no las soporta, se usa la
 * respuesta háptica estándar de la vista.
 */
object Haptics {
    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun compose(context: Context, vararg steps: Triple<Int, Float, Int>): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val vibrator = vibrator(context) ?: return false
        val primitives = steps.map { it.first }.distinct().toIntArray()
        if (!vibrator.hasVibrator() || !vibrator.areAllPrimitivesSupported(*primitives)) return false
        val composition = VibrationEffect.startComposition()
        steps.forEach { (primitive, scale, delay) -> composition.addPrimitive(primitive, scale, delay) }
        vibrator.vibrate(composition.compose())
        return true
    }

    /** Tres gotas que caen cada vez más fuerte: al soltar para actualizar. */
    fun raindrops(view: View) {
        val done = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && compose(
            view.context,
            Triple(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.4f, 0),
            Triple(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.7f, 70),
            Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 70),
        )
        if (!done) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            )
        }
    }

    /** Un toque corto y suave: al cambiar de ciudad o tocar una hora. */
    fun tick(view: View) {
        val done = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            compose(view.context, Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 0))
        if (!done) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
