package com.mnavarrosqa.clima.work

import android.content.Context
import com.mnavarrosqa.clima.widget.updateWeatherWidgets

/**
 * Lo que va después de bajar el clima, igual desde la app, el trabajo periódico o el ⟳ de un
 * widget: widgets, avisos y reloj. Cada aviso por separado: si uno falla, los demás igual salen.
 */
suspend fun afterRefresh(context: Context, urgentWear: Boolean = false) {
    runCatching { updateWeatherWidgets(context) }
    runCatching { RainNotifier.check(context) }
    runCatching { AlertNotifier.check(context) }
    runCatching { ChangeNotifier.check(context) }
    runCatching { TemperatureNotifier.update(context) }
    runCatching { SunNotifier.check(context) }
    runCatching { WearSync.send(context, urgent = urgentWear) }
}
