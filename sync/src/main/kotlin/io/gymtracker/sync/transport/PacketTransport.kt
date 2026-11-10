package io.gymtracker.sync.transport

import io.gymtracker.domain.model.AuthorId

/**
 * Un transporte mueve paquetes cifrados entre dos teléfonos. No conoce su contenido.
 * Implementaciones previstas: Nearby Connections (proximidad), archivo .gympkg
 * (hoja de compartir) y, en fase 5, relés cifrados.
 */
interface PacketTransport {
    val name: String

    /** Envía un paquete al amigo. Devuelve true solo cuando la entrega se confirmó. */
    suspend fun send(friendId: AuthorId, packet: ByteArray): Boolean
}
