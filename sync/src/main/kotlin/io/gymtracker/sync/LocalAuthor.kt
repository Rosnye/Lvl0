package io.gymtracker.sync

import io.gymtracker.domain.event.EventFactory
import io.gymtracker.domain.event.EventLog
import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.SignedEvent
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.domain.projection.Projector
import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoringEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Único punto por el que el usuario local anexa eventos a su registro. Asigna la seq,
 * firma y, tras cada sesión registrada, publica el resumen de puntaje para los amigos.
 */
@Singleton
class LocalAuthor @Inject constructor(
    identityManager: IdentityManager,
    private val eventLog: EventLog,
    private val scoringEngine: ScoringEngine,
) {
    private val identity = identityManager.identity
    private val factory = EventFactory(identity)
    private val mutex = Mutex()

    val authorId: AuthorId get() = identity.authorId

    suspend fun record(payload: EventPayload): SignedEvent = mutex.withLock { appendLocked(payload) }

    /**
     * Registra un evento que afecta al puntaje (sesión registrada o borrada) y publica
     * el resumen semanal actualizado para los amigos en el mismo paso.
     */
    suspend fun recordAndPublish(payload: EventPayload, today: LocalDate = LocalDate.now()): SignedEvent =
        mutex.withLock {
            val event = appendLocked(payload)
            val report = scoringEngine.compute(Projector.project(eventLog.readAll(authorId)), today)
            appendLocked(EventPayload.ScorePublished(report.summaryFor(today)))
            event
        }

    suspend fun currentState(): TrainingState = Projector.project(eventLog.readAll(authorId))

    /** Publica un resumen nuevo solo si cambió desde el último (racha rota, semana nueva...). */
    suspend fun publishScoreIfChanged(today: LocalDate = LocalDate.now()) = mutex.withLock {
        val events = eventLog.readAll(authorId)
        val summary = scoringEngine.compute(Projector.project(events), today).summaryFor(today)
        val last = events.lastOrNull { it.payload is EventPayload.ScorePublished }?.payload as? EventPayload.ScorePublished
        if (last?.summary != summary) appendLocked(EventPayload.ScorePublished(summary))
    }

    fun observeState(): Flow<TrainingState> = eventLog.observe(authorId).map(Projector::project)

    private suspend fun appendLocked(payload: EventPayload): SignedEvent {
        val event = factory.create(eventLog.lastSeq(authorId) + 1, payload)
        eventLog.append(event)
        return event
    }
}
