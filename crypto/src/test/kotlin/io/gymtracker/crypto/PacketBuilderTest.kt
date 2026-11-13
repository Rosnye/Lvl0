package io.gymtracker.crypto

import io.gymtracker.domain.event.EventFactory
import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.FriendSnapshot
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.model.ScoreSummary
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSession
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketBuilderTest {
    private val alice = Identity(MasterSeed(ByteArray(32) { 1 }))
    private val bob = Identity(MasterSeed(ByteArray(32) { 2 }))
    private val carol = Identity(MasterSeed(ByteArray(32) { 3 }))
    private val factory = EventFactory(alice) { 0 }

    private fun summary(week: String, points: Int) = EventPayload.ScorePublished(ScoreSummary(1, week, points, 1, points, 0, 1))

    /** Registro típico de Alice: privado y compartido mezclado, varias publicaciones por semana. */
    private val aliceLog = listOf(
        factory.create(1, EventPayload.ProfileUpdated(Profile("Alicia"))),
        factory.create(2, EventPayload.SessionLogged(WorkoutSession(SessionId("s"), "2026-11-02", 0, 60, emptyList()))),
        factory.create(3, summary("2026-11-02", 100)),
        factory.create(4, summary("2026-11-02", 220)),
        factory.create(5, EventPayload.ProfileUpdated(Profile("Ali"))),
        factory.create(6, summary("2026-11-09", 120)),
    )

    @Test
    fun `el snapshot lleva solo el ultimo perfil y el ultimo resumen por semana`() {
        val snapshot = FriendSnapshot.select(aliceLog)
        assertEquals(listOf(4L, 5L, 6L), snapshot.map { it.seq })
        assertEquals(listOf(6L), FriendSnapshot.select(aliceLog, maxWeeks = 1).filter { it.payload is EventPayload.ScorePublished }.map { it.seq })
    }

    @Test
    fun `bob abre el paquete de alice y valida sus eventos`() {
        val bytes = PacketBuilder.build(FriendSnapshot.select(aliceLog), bob.boxPublicKeyHex)
        val opened = PacketBuilder.open(bob, bytes) { it == alice.authorId }
        assertIs<PacketBuilder.Opened.Ok>(opened)
        assertEquals(alice.authorId, opened.author)
        assertEquals(3, opened.events.size)
    }

    @Test
    fun `un paquete para bob no lo abre carol, ni se acepta de un desconocido`() {
        val bytes = PacketBuilder.build(FriendSnapshot.select(aliceLog), bob.boxPublicKeyHex)
        assertEquals(PacketBuilder.Opened.Error(RejectReason.NOT_FOR_ME), PacketBuilder.open(carol, bytes) { true })
        assertEquals(PacketBuilder.Opened.Error(RejectReason.UNKNOWN_SENDER), PacketBuilder.open(bob, bytes) { false })
    }

    @Test
    fun `se rechazan eventos privados aunque esten firmados`() {
        val bytes = PacketBuilder.build(aliceLog, bob.boxPublicKeyHex) // incluye la sesión privada
        assertEquals(PacketBuilder.Opened.Error(RejectReason.INVALID_EVENT), PacketBuilder.open(bob, bytes) { true })
    }

    @Test
    fun `se rechaza un paquete alterado o vacio`() {
        val bytes = PacketBuilder.build(FriendSnapshot.select(aliceLog), bob.boxPublicKeyHex)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        assertEquals(PacketBuilder.Opened.Error(RejectReason.NOT_FOR_ME), PacketBuilder.open(bob, bytes) { true })
        val empty = PacketBuilder.build(emptyList(), bob.boxPublicKeyHex)
        assertEquals(PacketBuilder.Opened.Error(RejectReason.CORRUPT), PacketBuilder.open(bob, empty) { true })
    }

    @Test
    fun `un año de resumenes cabe holgado en un payload de Nearby`() {
        var monday = LocalDate.parse("2025-11-10")
        val log = (1L..60L).map { seq -> factory.create(seq, summary(monday.toString(), 300).also { monday = monday.plusWeeks(1) }) }
        val bytes = PacketBuilder.build(FriendSnapshot.select(log), bob.boxPublicKeyHex)
        assertTrue(bytes.size < 32 * 1024, "tamaño ${bytes.size}")
    }

    @Test
    fun `la invitacion viaja dentro de un texto y se rechaza si se altera`() {
        val invite = Invite.encode(alice.card("Alicia"))
        assertTrue(invite.startsWith("GT1:"))
        val card = Invite.decode("¡Agrégame en GymTracker! $invite gracias")
        assertNotNull(card)
        assertEquals(alice.authorId, card.authorId)
        assertEquals(alice.boxPublicKeyHex, card.boxPublicKeyHex)
        assertNull(Invite.decode(invite.dropLast(6) + "AAAAAA"))
        assertNull(Invite.decode("hola"))
    }
}
