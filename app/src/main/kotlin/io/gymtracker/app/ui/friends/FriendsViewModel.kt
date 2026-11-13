package io.gymtracker.app.ui.friends

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.app.friends.IncomingPackets
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.domain.model.Weeks
import io.gymtracker.domain.scoring.ScoringEngine
import io.gymtracker.sync.AddFriendResult
import io.gymtracker.sync.Friend
import io.gymtracker.sync.FriendRepository
import io.gymtracker.sync.PacketCodec
import io.gymtracker.sync.WorkoutRepository
import io.gymtracker.sync.transport.FilePacketTransport
import io.gymtracker.sync.transport.NearbySync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

enum class Board { WEEK, TOTAL }

data class LeaderRow(
    val id: String,
    val nickname: String,
    val isMe: Boolean,
    val weekPoints: Int,
    val totalPoints: Int,
    val streak: Int,
    val level: Int,
)

/** Mensaje puntual tras agregar un amigo. */
sealed interface FriendsMessage {
    data class Added(val nickname: String) : FriendsMessage
    data class Already(val nickname: String) : FriendsMessage
    data object Invalid : FriendsMessage
    data object Self : FriendsMessage
    data object ScanFailed : FriendsMessage
}

data class FriendsUiState(
    val nickname: String? = null,
    val fingerprint: String = "",
    val invite: String? = null,
    val board: Board = Board.WEEK,
    val rows: List<LeaderRow> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val nearby: NearbySync.Status = NearbySync.Status(),
    val message: FriendsMessage? = null,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    workoutRepository: WorkoutRepository,
    scoringEngine: ScoringEngine,
    private val packetCodec: PacketCodec,
    private val fileTransport: FilePacketTransport,
    private val nearbySync: NearbySync,
    private val incomingPackets: IncomingPackets,
) : ViewModel() {
    private val board = MutableStateFlow(Board.WEEK)
    private val message = MutableStateFlow<FriendsMessage?>(null)

    private val base = combine(
        friendRepository.observeProfile(),
        friendRepository.observeFriends(),
        workoutRepository.observeState(),
        board,
    ) { profile, friends, training, selected ->
        val today = LocalDate.now()
        val week = Weeks.startOf(today)
        val report = scoringEngine.compute(training, today)
        val me = LeaderRow(
            id = friendRepository.myId.hex,
            nickname = profile?.nickname ?: "",
            isMe = true,
            weekPoints = report.pointsByWeek[week] ?: 0,
            totalPoints = report.totalPoints,
            streak = report.currentStreakWeeks,
            level = report.level,
        )
        val others = friends.map { f ->
            val latest = f.feed.latest
            val recent = latest != null && LocalDate.parse(latest.weekStart) >= week.minusWeeks(1)
            LeaderRow(
                id = f.entity.authorId,
                nickname = f.nickname,
                isMe = false,
                weekPoints = f.feed.pointsForWeek(week.toString()),
                totalPoints = latest?.totalPoints ?: 0,
                streak = if (recent) latest.streakWeeks else 0,
                level = latest?.level ?: 1,
            )
        }
        val rows = (others + me).sortedWith(
            compareByDescending<LeaderRow> { if (selected == Board.WEEK) it.weekPoints else it.totalPoints }.thenBy { !it.isMe },
        )
        FriendsUiState(
            nickname = profile?.nickname,
            fingerprint = friendRepository.myId.fingerprint,
            invite = profile?.nickname?.let(friendRepository::myInvite),
            board = selected,
            rows = rows,
            friends = friends.sortedBy { it.nickname.lowercase() },
        )
    }

    val state: StateFlow<FriendsUiState> = combine(base, nearbySync.status, message) { s, nearby, msg ->
        s.copy(nearby = nearby, message = msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    fun setBoard(value: Board) { board.value = value }

    fun setNickname(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { friendRepository.setNickname(name) }
    }

    fun addFromText(text: String) {
        viewModelScope.launch {
            message.value = when (val r = friendRepository.addFromText(text)) {
                is AddFriendResult.Added -> FriendsMessage.Added(r.nickname)
                is AddFriendResult.AlreadyFriend -> FriendsMessage.Already(r.nickname)
                AddFriendResult.Invalid -> FriendsMessage.Invalid
                AddFriendResult.Self -> FriendsMessage.Self
            }
        }
    }

    fun scanFailed() { message.value = FriendsMessage.ScanFailed }
    fun consumeMessage() { message.value = null }

    fun remove(id: AuthorId) {
        viewModelScope.launch { friendRepository.remove(id) }
    }

    /** Prepara el archivo cifrado para un amigo; la pantalla lo comparte con la hoja del sistema. */
    fun preparePacket(friend: Friend, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val bytes = packetCodec.buildFor(friend.authorId) ?: return@launch
            onReady(fileTransport.writePacket(friend.nickname, friend.authorId, bytes))
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch { incomingPackets.import(uri) }
    }

    fun startNearby() = nearbySync.start()
    fun stopNearby() = nearbySync.stop()
}
