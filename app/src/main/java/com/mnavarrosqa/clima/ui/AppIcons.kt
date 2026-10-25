package com.mnavarrosqa.clima.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.mnavarrosqa.clima.R
import com.mnavarrosqa.clima.core.R as CoreR
import com.mnavarrosqa.clima.data.tr

/**
 * Íconos de la app. Cada uno es un activity-alias del manifiesto; se prende el elegido y se
 * apagan los demás. El launcher puede tardar unos segundos en mostrar el cambio.
 */
enum class AppIcon(
    private val alias: String,
    private val es: String,
    private val en: String,
    @DrawableRes val background: Int,
    @DrawableRes val foreground: Int,
) {
    MONOGRAMA(".MainActivity", "Monograma", "Monogram", CoreR.drawable.ic_launcher_background, CoreR.drawable.ic_launcher_foreground),
    NOCHE(".IconoNoche", "Noche", "Night", R.drawable.ic_launcher_noche_background, R.drawable.ic_launcher_noche_foreground),
    ATARDECER(".IconoAtardecer", "Atardecer", "Sunset", R.drawable.ic_launcher_atardecer_background, R.drawable.ic_launcher_atardecer_foreground),
    CLARO(".IconoClaro", "Claro", "Light", R.drawable.ic_launcher_claro_background, R.drawable.ic_launcher_claro_foreground),
    ;

    val label: String get() = tr(es, en)

    private fun component(context: Context) = ComponentName(context.packageName, "com.mnavarrosqa.clima$alias")

    companion object {
        /** El que está prendido ahora; si el sistema no dice nada, el que viene por defecto. */
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull { icon ->
                when (pm.getComponentEnabledSetting(icon.component(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == MONOGRAMA
                    else -> false
                }
            } ?: MONOGRAMA
        }

        fun set(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            // Primero se prende el nuevo: si no, por un instante la app quedaría sin ícono.
            pm.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
