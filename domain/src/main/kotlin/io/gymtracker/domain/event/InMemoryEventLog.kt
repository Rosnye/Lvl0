package io.gymtracker.domain.event

import io.gymtracker.domain.model.AuthorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Implementación en memoria para pruebas y prototipos. */
class InMemoryEventLog : EventLog {
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<AuthorId, List<SignedEvent>>>(emptyMap())

    override suspend fun append(event: SignedEvent) = mutex.withLock {
        val current = state.value[event.authorId].orEmpty()
        val last = current.lastOrNull()?.seq ?: 0L
        if (event.seq <= last) {
            throw SequenceException("seq ${event.seq} no es mayor que la última ($last) de ${event.authorId.fingerprint}")
        }
        state.value = state.value + (event.authorId to current + event)
    }

    override suspend fun lastSeq(author: AuthorId): Long = state.value[author]?.lastOrNull()?.seq ?: 0L

    override suspend fun readAll(author: AuthorId): List<SignedEvent> = state.value[author].orEmpty()

    override suspend fun readSince(author: AuthorId, afterSeq: Long, visibility: Visibility?): List<SignedEvent> =
        state.value[author].orEmpty().filter { it.seq > afterSeq && (visibility == null || it.body.visibility == visibility) }

    override fun observe(author: AuthorId): Flow<List<SignedEvent>> = state.map { it[author].orEmpty() }

    override suspend fun authors(): List<AuthorId> = state.value.keys.toList()
}
