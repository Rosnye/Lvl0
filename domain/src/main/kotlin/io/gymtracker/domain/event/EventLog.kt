package io.gymtracker.domain.event

import io.gymtracker.domain.model.AuthorId
import kotlinx.coroutines.flow.Flow

/**
 * Registro de eventos solo-anexar, por autor. Guarda el registro propio completo y
 * las proyecciones FRIENDS recibidas de amigos (con huecos de seq esperables).
 */
interface EventLog {
    /** Anexa un evento. Falla si seq no es mayor que la última conocida para ese autor. */
    suspend fun append(event: SignedEvent)

    /** Última seq conocida del autor, 0 si no hay eventos. */
    suspend fun lastSeq(author: AuthorId): Long

    suspend fun readAll(author: AuthorId): List<SignedEvent>

    suspend fun readSince(author: AuthorId, afterSeq: Long, visibility: Visibility? = null): List<SignedEvent>

    fun observe(author: AuthorId): Flow<List<SignedEvent>>

    /** Autores con al menos un evento (el propio y los amigos). */
    suspend fun authors(): List<AuthorId>
}

class SequenceException(message: String) : IllegalArgumentException(message)
