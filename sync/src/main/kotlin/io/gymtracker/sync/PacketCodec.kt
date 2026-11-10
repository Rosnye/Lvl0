package io.gymtracker.sync

import io.gymtracker.crypto.Ed25519Verifier
import io.gymtracker.crypto.PacketCipher
import io.gymtracker.crypto.PacketFormatException
import io.gymtracker.data.db.FriendDao
import io.gymtracker.domain.event.EventCodec
import io.gymtracker.domain.event.Packet
import io.gymtracker.domain.event.EventLog
import io.gymtracker.domain.event.EventValidator
import io.gymtracker.domain.event.ValidationResult
import io.gymtracker.domain.event.Visibility
import io.gymtracker.domain.model.AuthorId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construye paquetes cifrados para un amigo y procesa los recibidos. Independiente del
 * transporte: Nearby, archivo o (en el futuro) relés usan este mismo código.
 */
@Singleton
class PacketCodec @Inject constructor(
    private val identityManager: IdentityManager,
    private val eventLog: EventLog,
    private val friendDao: FriendDao,
) {
    /** Paquete con los eventos FRIENDS aún no enviados a ese amigo, o null si no hay nada nuevo. */
    suspend fun buildFor(friendId: AuthorId): Outgoing? {
        val friend = friendDao.get(friendId.hex) ?: return null
        val me = identityManager.identity.authorId
        val events = eventLog.readSince(me, friend.lastSentSeq, Visibility.FRIENDS)
        if (events.isEmpty()) return null
        val plaintext = EventCodec.canonical.encodeToString(Packet.serializer(), Packet(events = events)).encodeToByteArray()
        return Outgoing(friendId, events.last().seq, PacketCipher.seal(plaintext, friend.boxPublicKeyHex))
    }

    /** Llamar solo cuando el transporte confirme la entrega. */
    suspend fun markDelivered(outgoing: Outgoing) = friendDao.markSent(outgoing.friendId.hex, outgoing.upToSeq)

    suspend fun receive(packet: ByteArray): ReceiveResult {
        val plaintext = try {
            identityManager.identity.open(packet)
        } catch (e: PacketFormatException) {
            return ReceiveResult.Rejected(e.message ?: "paquete inválido")
        }
        val parsed = runCatching { EventCodec.lenient.decodeFromString(Packet.serializer(), plaintext.decodeToString()) }
            .getOrElse { return ReceiveResult.Rejected("contenido ilegible") }

        val authors = parsed.events.map { it.authorId }.distinct()
        val author = authors.singleOrNull() ?: return ReceiveResult.Rejected("un paquete debe tener un solo autor")
        val friend = friendDao.get(author.hex) ?: return ReceiveResult.Rejected("autor desconocido: no es un amigo vinculado")

        var accepted = 0
        var last = eventLog.lastSeq(author)
        for (event in parsed.events.sortedBy { it.seq }) {
            if (event.seq <= last) continue // ya lo teníamos
            when (val v = EventValidator.validateIncoming(event, Ed25519Verifier)) {
                is ValidationResult.Rejected -> return ReceiveResult.Rejected(v.reason)
                ValidationResult.Ok -> {
                    eventLog.append(event)
                    last = event.seq
                    accepted++
                }
            }
        }
        return ReceiveResult.Accepted(friend.nickname, accepted)
    }

    data class Outgoing(val friendId: AuthorId, val upToSeq: Long, val bytes: ByteArray)
}
