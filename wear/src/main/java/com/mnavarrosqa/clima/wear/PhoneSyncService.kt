package com.mnavarrosqa.clima.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.mnavarrosqa.clima.data.WEAR_SYNC_PATH

/** Recibe lo que manda el teléfono (pronóstico, unidades y nombre) y actualiza tarjeta y complicación. */
class PhoneSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != WEAR_SYNC_PATH) return@forEach
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            WearStore.saveFromPhone(this, map.getString("snapshot"), map.getString("units"))
        }
    }
}
