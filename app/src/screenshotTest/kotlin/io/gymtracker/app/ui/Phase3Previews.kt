package io.gymtracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.gymtracker.app.ui.friends.Board
import io.gymtracker.app.ui.friends.FriendsContent
import io.gymtracker.app.ui.friends.FriendsUiState
import io.gymtracker.app.ui.friends.LeaderRow
import io.gymtracker.app.ui.friends.MyCodeContent
import io.gymtracker.app.ui.theme.GymTrackerTheme
import io.gymtracker.crypto.Identity
import io.gymtracker.crypto.Invite
import io.gymtracker.crypto.MasterSeed
import io.gymtracker.data.db.FriendEntity
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.projection.FriendFeed
import io.gymtracker.sync.Friend

private const val PHONE = "spec:width=411dp,height=891dp,dpi=420"
private const val TALL = "spec:width=411dp,height=1500dp,dpi=420"

private val me = Identity(MasterSeed(ByteArray(32) { 7 }))
private fun id(seed: Int) = Identity(MasterSeed(ByteArray(32) { seed.toByte() })).authorId.hex

private fun friend(seed: Int, nickname: String, synced: Long) = Friend(
    FriendEntity(id(seed), "00".repeat(32), nickname, addedAtEpochMs = 0, lastSyncEpochMs = synced),
    FriendFeed(AuthorId(id(seed)), Profile(nickname), emptyMap()),
)

private val friends = listOf(
    friend(11, "Andrea", 1_762_900_000_000),
    friend(12, "Marcos", 1_762_700_000_000),
    friend(13, "Lucía", 0),
)

private val rows = listOf(
    LeaderRow(id(11), "Andrea", false, weekPoints = 412, totalPoints = 5_210, streak = 6, level = 4),
    LeaderRow(me.authorId.hex, "Alex", true, weekPoints = 371, totalPoints = 2_963, streak = 5, level = 3),
    LeaderRow(id(12), "Marcos", false, weekPoints = 220, totalPoints = 3_480, streak = 2, level = 3),
    LeaderRow(id(13), "Lucía", false, weekPoints = 0, totalPoints = 0, streak = 0, level = 1),
)

private val friendsState = FriendsUiState(
    nickname = "Alex",
    fingerprint = me.authorId.fingerprint,
    invite = Invite.encode(me.card("Alex")),
    board = Board.WEEK,
    rows = rows,
    friends = friends,
)

@Composable
private fun Shell(content: @Composable () -> Unit) {
    GymTrackerTheme(darkTheme = true) {
        AppScaffold(selected = Destination.FRIENDS, onSelect = {}) { padding -> Box(Modifier.padding(padding)) { content() } }
    }
}

@PreviewTest
@Preview(name = "Amigos", device = PHONE, showSystemUi = true)
@Composable
fun FriendsPreview() = Shell { FriendsContent(friendsState) }

@PreviewTest
@Preview(name = "Amigos completo", device = TALL)
@Composable
fun FriendsFullPreview() = Shell { FriendsContent(friendsState) }

@PreviewTest
@Preview(name = "Amigos en espanol", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun FriendsSpanishPreview() = Shell { FriendsContent(friendsState, language = io.gymtracker.app.settings.AppLanguage.SPANISH) }

@PreviewTest
@Preview(name = "Elegir apodo", device = PHONE, showSystemUi = true)
@Composable
fun NicknamePreview() = Shell { FriendsContent(FriendsUiState(fingerprint = me.authorId.fingerprint)) }

@PreviewTest
@Preview(name = "Mi codigo", device = PHONE, showSystemUi = true)
@Composable
fun MyCodePreview() = Shell {
    Card(Modifier.padding(24.dp)) {
        MyCodeContent(friendsState.invite!!, "Alex", me.authorId.fingerprint, Modifier.padding(24.dp))
    }
}
