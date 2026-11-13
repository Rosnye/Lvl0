package io.gymtracker.sync

import io.gymtracker.crypto.PacketBuilder
import io.gymtracker.crypto.RejectReason
import io.gymtracker.data.db.FriendDao
import io.gymtracker.domain.event.EventLog
import io.gymtracker.domain.event.FriendSnapshot
import io.gymtracker.domain.model.AuthorId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Construye paquetes cifrados para un amigo y procesa los recibidos. Lo usan todos los
 * transportes (archivo y Nearby). Cada paquete lleva una instantánea completa
 * ([FriendSnapshot]), así recibir dos veces lo mismo no tiene efecto.
 */
@Singleton
class PacketCodec @Inject constructor(
    private val identityManager: IdentityManager,
    private val eventLog: EventLog,
    private val friendDao: FriendDao,
    private val localAuthor: LocalAuthor,
) {
    suspend fun buildFor(friendId: AuthorId, maxWeeks: Int = FriendSnapshot.DEFAULT_MAX_WEEKS): ByteArray? {
        val friend = friendDao.get(friendId.hex) ?: return null
        localAuthor.publishScoreIfChanged()
        val events = FriendSnapshot.select(eventLog.readAll(identityManager.identity.authorId), maxWeeks)
        return PacketBuilder.build(events, friend.boxPublicKeyHex)
    }

    suspend fun receive(bytes: ByteArray, now: Long = System.currentTimeMillis()): ReceiveResult {
        if (bytes.size > MAX_PACKET_BYTES) return ReceiveResult.Rejected(RejectReason.CORRUPT)
        val friends = friendDao.all().associateBy { it.authorId }
        return when (val opened = PacketBuilder.open(identityManager.identity, bytes) { it.hex in friends }) {
            is PacketBuilder.Opened.Error -> ReceiveResult.Rejected(opened.reason)
            is PacketBuilder.Opened.Ok -> {
                var last = eventLog.lastSeq(opened.author)
                var accepted = 0
                for (event in opened.events) {
                    if (event.seq <= last) continue // ya lo teníamos
                    eventLog.append(event)
                    last = event.seq
                    accepted++
                }
                friendDao.markSynced(opened.author.hex, now)
                ReceiveResult.Accepted(friends.getValue(opened.author.hex).nickname, accepted)
            }
        }
    }

    companion object {
        const val MAX_PACKET_BYTES = 1024 * 1024
    }
}
