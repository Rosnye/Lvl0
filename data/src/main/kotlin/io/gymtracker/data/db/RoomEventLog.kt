package io.gymtracker.data.db

import io.gymtracker.domain.event.EventCodec
import io.gymtracker.domain.event.EventLog
import io.gymtracker.domain.event.SequenceException
import io.gymtracker.domain.event.SignedEvent
import io.gymtracker.domain.event.Visibility
import io.gymtracker.domain.model.AuthorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomEventLog(private val dao: EventDao) : EventLog {
    private val appendMutex = Mutex()

    override suspend fun append(event: SignedEvent) = appendMutex.withLock {
        val last = dao.lastSeq(event.authorId.hex) ?: 0L
        if (event.seq <= last) {
            throw SequenceException("seq ${event.seq} no es mayor que la última ($last) de ${event.authorId.fingerprint}")
        }
        dao.insert(event.toEntity())
    }

    override suspend fun lastSeq(author: AuthorId): Long = dao.lastSeq(author.hex) ?: 0L

    override suspend fun readAll(author: AuthorId): List<SignedEvent> = dao.all(author.hex).map { it.toEvent() }

    override suspend fun readSince(author: AuthorId, afterSeq: Long, visibility: Visibility?): List<SignedEvent> =
        (if (visibility == null) dao.since(author.hex, afterSeq) else dao.since(author.hex, afterSeq, visibility.name))
            .map { it.toEvent() }

    override fun observe(author: AuthorId): Flow<List<SignedEvent>> =
        dao.observe(author.hex).map { list -> list.map { it.toEvent() } }

    override suspend fun authors(): List<AuthorId> = dao.authors().map(::AuthorId)
}

internal fun SignedEvent.toEntity() = EventEntity(
    authorId = authorId.hex,
    seq = seq,
    createdAtEpochMs = body.createdAtEpochMs,
    visibility = body.visibility.name,
    type = payload.kind,
    eventJson = EventCodec.encode(this),
)

internal fun EventEntity.toEvent(): SignedEvent = EventCodec.decode(eventJson)
