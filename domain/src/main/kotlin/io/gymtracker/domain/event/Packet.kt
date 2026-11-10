package io.gymtracker.domain.event

import kotlinx.serialization.Serializable

/** Contenido en claro de un paquete hacia un amigo: solo eventos FRIENDS de un único autor. */
@Serializable
data class Packet(
    val version: Int = 1,
    val events: List<SignedEvent>,
)
