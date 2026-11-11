package io.gymtracker.domain

import io.gymtracker.domain.event.EventCodec
import io.gymtracker.domain.event.EventFactory
import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.EventValidator
import io.gymtracker.domain.event.InMemoryEventLog
import io.gymtracker.domain.event.SequenceException
import io.gymtracker.domain.event.ValidationResult
import io.gymtracker.domain.event.Visibility
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.model.ScoreSummary
import io.gymtracker.domain.projection.FriendProjector
import io.gymtracker.domain.projection.Projector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EventTest {
    private val signer = FakeSigner()
    private val factory = EventFactory(signer) { 1_700_000_000_000 }

    @Test
    fun `la serializacion canonica es estable y se puede firmar y verificar`() {
        val event = factory.create(1, EventPayload.SessionLogged(session("2026-11-09")))
        val json = EventCodec.encode(event)
        val decoded = EventCodec.decode(json)
        assertEquals(event, decoded)
        assertEquals(EventCodec.canonicalBytes(event.body).toList(), EventCodec.canonicalBytes(decoded.body).toList())
        assertEquals(ValidationResult.Ok, EventValidator.validate(decoded, signer))
        assertTrue(json.contains("\"type\":\"session_logged\""))
    }

    @Test
    fun `la visibilidad la fija el tipo de evento`() {
        assertEquals(Visibility.PRIVATE, factory.create(1, EventPayload.SessionLogged(session("2026-11-09"))).body.visibility)
        assertEquals(Visibility.FRIENDS, factory.create(2, EventPayload.ProfileUpdated(Profile("Dav"))).body.visibility)
    }

    @Test
    fun `un evento manipulado no pasa la validacion`() {
        val event = factory.create(1, EventPayload.ProfileUpdated(Profile("Dav")))
        val tampered = event.copy(body = event.body.copy(payload = EventPayload.ProfileUpdated(Profile("Otro"))))
        assertIs<ValidationResult.Rejected>(EventValidator.validate(tampered, signer))
    }

    @Test
    fun `un receptor rechaza eventos privados aunque esten bien firmados`() {
        val event = factory.create(1, EventPayload.SessionLogged(session("2026-11-09")))
        assertIs<ValidationResult.Rejected>(EventValidator.validateIncoming(event, signer))
        val pub = factory.create(2, EventPayload.ScorePublished(ScoreSummary(1, "2026-11-09", 100, 1, 100, 0, 1)))
        assertEquals(ValidationResult.Ok, EventValidator.validateIncoming(pub, signer))
    }

    @Test
    fun `el registro exige seq estrictamente creciente`() = runTest {
        val log = InMemoryEventLog()
        log.append(factory.create(1, EventPayload.ProfileUpdated(Profile("a"))))
        log.append(factory.create(3, EventPayload.ProfileUpdated(Profile("b")))) // hueco permitido (feeds de amigos)
        assertFailsWith<SequenceException> { log.append(factory.create(2, EventPayload.ProfileUpdated(Profile("c")))) }
        assertEquals(3, log.lastSeq(signer.authorId))
        assertEquals(1, log.readSince(signer.authorId, afterSeq = 1).size)
    }

    @Test
    fun `la proyeccion aplica borrados como lapidas`() {
        val s = session("2026-11-09")
        val events = listOf(
            factory.create(1, EventPayload.SessionLogged(s)),
            factory.create(2, EventPayload.SessionDeleted(s.id)),
            factory.create(3, EventPayload.SessionLogged(s)), // re-registro posterior se ignora
            factory.create(4, EventPayload.SessionLogged(session("2026-11-10"))),
        )
        val state = Projector.project(events.shuffled())
        assertEquals(listOf("2026-11-10"), state.sessions.map { it.localDate })
    }

    @Test
    fun `el feed de un amigo conserva el ultimo resumen por semana`() {
        val events = listOf(
            factory.create(1, EventPayload.ProfileUpdated(Profile("Dav"))),
            factory.create(2, EventPayload.ScorePublished(ScoreSummary(1, "2026-11-02", 100, 1, 100, 0, 1))),
            factory.create(3, EventPayload.ScorePublished(ScoreSummary(1, "2026-11-02", 200, 2, 200, 0, 1))),
            factory.create(4, EventPayload.ScorePublished(ScoreSummary(1, "2026-11-09", 100, 1, 300, 1, 1))),
        )
        val feed = FriendProjector.project(signer.authorId, events)
        assertEquals("Dav", feed.profile?.nickname)
        assertEquals(200, feed.pointsForWeek("2026-11-02"))
        assertEquals(300, feed.latest?.totalPoints)
    }
}

class DraftTest {
    @Test
    fun `el borrador solo conserva series hechas y descarta ejercicios vacios`() {
        val draft = io.gymtracker.domain.model.WorkoutDraft(
            id = io.gymtracker.domain.model.SessionId("d1"),
            startedAtEpochMs = 1_762_800_000_000, // 2025-11-10 aprox. UTC
            entries = listOf(
                io.gymtracker.domain.model.DraftEntry(SQUAT, listOf(
                    io.gymtracker.domain.model.DraftSet(reps = 5, weightGrams = 100_000, done = true),
                    io.gymtracker.domain.model.DraftSet(reps = 5, weightGrams = 100_000, done = false),
                )),
                io.gymtracker.domain.model.DraftEntry(BENCH, listOf(io.gymtracker.domain.model.DraftSet(reps = 8))),
            ),
        )
        val session = draft.toSession(finishedAtEpochMs = 1_762_800_000_000 + 3_600_000, zone = java.time.ZoneOffset.UTC)
        assertEquals(1, session.entries.size)
        assertEquals(1, session.entries[0].sets.size)
        assertEquals(3600, session.durationSeconds)
        assertEquals("2025-11-10", session.localDate)
    }

    @Test
    fun `el catalogo precargado tiene ids unicos y esta en el estado proyectado`() {
        val all = io.gymtracker.domain.model.BuiltInExercises.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        val state = Projector.project(emptyList())
        assertTrue(state.exercises.size >= 50)
    }
}
