package io.gymtracker.sync

import io.gymtracker.crypto.FriendCard
import io.gymtracker.data.db.FriendDao
import io.gymtracker.data.db.FriendEntity
import io.gymtracker.domain.event.EventLog
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.domain.projection.FriendFeed
import io.gymtracker.domain.projection.FriendProjector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(
    private val friendDao: FriendDao,
    private val eventLog: EventLog,
    private val identityManager: IdentityManager,
) {
    /** Vincula a partir del QR del amigo. Devuelve false si la tarjeta no es válida o es la propia. */
    suspend fun addFromCard(text: String, now: Long = System.currentTimeMillis()): Boolean {
        val card = FriendCard.decode(text) ?: return false
        if (card.authorId == identityManager.identity.authorId) return false
        friendDao.upsert(FriendEntity(card.authorId.hex, card.boxPublicKeyHex, card.nickname, now))
        return true
    }

    /** Deja de aceptar paquetes del amigo. Sus eventos ya recibidos se conservan hasta el borrado (fase 4). */
    suspend fun remove(authorId: AuthorId) {
        friendDao.get(authorId.hex)?.let { friendDao.delete(it) }
    }

    fun observeFriends(): Flow<List<FriendEntity>> = friendDao.observeAll()

    suspend fun feedOf(authorId: AuthorId): FriendFeed = FriendProjector.project(authorId, eventLog.readAll(authorId))

    fun observeFeed(authorId: AuthorId): Flow<FriendFeed> =
        eventLog.observe(authorId).map { FriendProjector.project(authorId, it) }

    fun myCard(nickname: String): FriendCard = identityManager.identity.card(nickname)
}
