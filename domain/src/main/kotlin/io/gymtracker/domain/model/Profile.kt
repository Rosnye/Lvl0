package io.gymtracker.domain.model

import kotlinx.serialization.Serializable

/** Lo único "personal" que se comparte con amigos: un apodo elegido y un avatar predefinido. */
@Serializable
data class Profile(
    val nickname: String,
    val avatarId: Int = 0,
)
