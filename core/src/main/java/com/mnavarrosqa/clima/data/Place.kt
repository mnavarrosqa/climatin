package com.mnavarrosqa.clima.data

import kotlinx.serialization.Serializable

/** Id reservado para "donde estoy". */
const val CURRENT_PLACE_ID = "current"

/** Ciudad guardada a mano desde el buscador. */
@Serializable
data class Place(
    val id: String,
    val name: String,
    val detail: String?,
    val latitude: Double,
    val longitude: Double,
    /** Apodo que le puso la persona ("Casa", "Lo de mamá"); se muestra en vez del nombre. */
    val nickname: String? = null,
) {
    val shownName: String get() = nickname ?: name
}
