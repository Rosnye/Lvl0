package io.gymtracker.crypto

import io.gymtracker.domain.event.EventFactory
import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.EventValidator
import io.gymtracker.domain.event.ValidationResult
import io.gymtracker.domain.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoTest {
    private val seedA = MasterSeed(ByteArray(32) { 1 })
    private val seedB = MasterSeed(ByteArray(32) { 2 })
    private val alice = Identity(seedA)
    private val bob = Identity(seedB)

    @Test
    fun `la identidad se deriva de forma determinista de la semilla`() {
        val again = Identity(MasterSeed(ByteArray(32) { 1 }))
        assertEquals(alice.authorId, again.authorId)
        assertEquals(alice.boxPublicKeyHex, again.boxPublicKeyHex)
        assertNotEquals(alice.authorId, bob.authorId)
        assertNotEquals(alice.authorId.hex, alice.boxPublicKeyHex)
    }

    @Test
    fun `los eventos firmados con Ed25519 se verifican y detectan manipulacion`() {
        val event = EventFactory(alice) { 0 }.create(1, EventPayload.ProfileUpdated(Profile("alice")))
        assertEquals(ValidationResult.Ok, EventValidator.validate(event, Ed25519Verifier))
        val tampered = event.copy(body = event.body.copy(payload = EventPayload.ProfileUpdated(Profile("mallory"))))
        assertIs<ValidationResult.Rejected>(EventValidator.validate(tampered, Ed25519Verifier))
        val wrongAuthor = event.copy(body = event.body.copy(authorId = bob.authorId))
        assertIs<ValidationResult.Rejected>(EventValidator.validate(wrongAuthor, Ed25519Verifier))
    }

    @Test
    fun `un paquete solo lo abre el destinatario`() {
        val message = "hola bob".encodeToByteArray()
        val packet = PacketCipher.seal(message, bob.boxPublicKeyHex)
        assertEquals("hola bob", bob.open(packet).decodeToString())
        assertFailsWith<PacketFormatException> { alice.open(packet) }
    }

    @Test
    fun `cada paquete es distinto aunque el contenido sea el mismo`() {
        val message = "x".encodeToByteArray()
        val p1 = PacketCipher.seal(message, bob.boxPublicKeyHex)
        val p2 = PacketCipher.seal(message, bob.boxPublicKeyHex)
        assertFalse(p1.contentEquals(p2))
    }

    @Test
    fun `un paquete alterado se rechaza`() {
        val packet = PacketCipher.seal("hola".encodeToByteArray(), bob.boxPublicKeyHex)
        packet[packet.size - 1] = (packet.last().toInt() xor 1).toByte()
        assertFailsWith<PacketFormatException> { bob.open(packet) }
        assertFailsWith<PacketFormatException> { bob.open(byteArrayOf(9, 9, 9)) }
    }

    @Test
    fun `la tarjeta de amigo viaja firmada y se rechaza si cambian la clave de cifrado`() {
        val card = alice.card("Alice")
        assertTrue(card.isValid())
        val roundTrip = FriendCard.decode(card.encode())
        assertNotNull(roundTrip)
        assertEquals(alice.authorId, roundTrip.authorId)
        val swapped = card.copy(boxPublicKeyHex = bob.boxPublicKeyHex)
        assertNull(FriendCard.decode(swapped.encode()))
        assertNull(FriendCard.decode("no es json"))
    }
}
