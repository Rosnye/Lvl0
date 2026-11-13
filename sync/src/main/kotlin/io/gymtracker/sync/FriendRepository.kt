package io.gymtracker.sync

import io.gymtracker.crypto.FriendCard
import io.gymtracker.crypto.Invite
import io.gymtracker.data.db.FriendDao
import io.gymtracker.data.db.FriendEntity
import io.gymtracker.domain.event.EventLog
import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.projection.FriendFeed
import io.gymtracker.domain.projection.FriendProjector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Friend(val entity: FriendEntity, val feed: FriendFeed) {
    val authorId: AuthorId get() = AuthorId(entity.authorId)
    /** El apodo más reciente que publicó el amigo, o el de su tarjeta. */
    val nickname: String get() = feed.profile?.nickname ?: entity.nickname
}

sealed interface AddFriendResult {
    data class Added(val nickname: String) : AddFriendResult
    data class AlreadyFriend(val nickname: String) : AddFriendResult
    data object Invalid : AddFriendResult
    data object Self : AddFriendResult
}

@Singleton
class FriendRepository @Inject constructor(
    private val friendDao: FriendDao,
    private val eventLog: EventLog,
    private val identityManager: IdentityManager,
    private val localAuthor: LocalAuthor,
) {
    val myId: AuthorId get() = identityManager.identity.authorId

    /** Vincula a partir de un QR o de una invitación pegada. */
    suspend fun addFromText(text: String, now: Long = System.currentTimeMillis()): AddFriendResult {
        val card = Invite.decode(text) ?: return AddFriendResult.Invalid
        if (card.authorId == myId) return AddFriendResult.Self
        friendDao.get(card.authorId.hex)?.let { return AddFriendResult.AlreadyFriend(it.nickname) }
        friendDao.upsert(FriendEntity(card.authorId.hex, card.boxPublicKeyHex, card.nickname, now))
        return AddFriendResult.Added(card.nickname)
    }

    /** Deja de aceptar paquetes del amigo y lo quita del tablero. */
    suspend fun remove(authorId: AuthorId) {
        friendDao.get(authorId.hex)?.let { friendDao.delete(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFriends(): Flow<List<Friend>> = friendDao.observeAll().flatMapLatest { entities ->
        if (entities.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(entities.map { entity ->
                val id = AuthorId(entity.authorId)
                eventLog.observe(id).map { Friend(entity, FriendProjector.project(id, it)) }
            }) { it.toList() }
        }
    }

    fun observeProfile(): Flow<Profile?> = localAuthor.observeState().map { it.profile }

    suspend fun setNickname(nickname: String) {
        localAuthor.record(EventPayload.ProfileUpdated(Profile(nickname.trim().take(30))))
    }

    fun myCard(nickname: String): FriendCard = identityManager.identity.card(nickname)

    fun myInvite(nickname: String): String = Invite.encode(myCard(nickname))
}
