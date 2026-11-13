package io.gymtracker.app.ui.friends

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.Format
import io.gymtracker.sync.Friend
import io.gymtracker.sync.transport.FilePacketTransport
import io.gymtracker.sync.transport.NearbySync
import java.io.File
import java.time.Instant
import java.time.ZoneId

@Composable
fun FriendsScreen(viewModel: FriendsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showNearby by remember { mutableStateOf(false) }
    var nearbyDenied by remember { mutableStateOf(false) }

    val permissions = remember { nearbyPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            viewModel.startNearby()
            showNearby = true
        } else {
            nearbyDenied = true
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }

    FriendsContent(
        state = state,
        onNickname = viewModel::setNickname,
        onBoard = viewModel::setBoard,
        onScan = { scanQr(context, onResult = viewModel::addFromText, onError = viewModel::scanFailed) },
        onPaste = viewModel::addFromText,
        onShareInvite = { invite -> shareText(context, context.getString(R.string.friends_invite_message, invite)) },
        onSend = { friend ->
            viewModel.preparePacket(friend) { file -> shareFile(context, file, context.getString(R.string.sync_share_title, friend.nickname)) }
        },
        onRemove = { viewModel.remove(it.authorId) },
        onNearby = { permissionLauncher.launch(permissions) },
        onImport = { importLauncher.launch(arrayOf("*/*")) },
        onMessageShown = viewModel::consumeMessage,
    )

    if (showNearby) {
        NearbyDialog(state.nearby, onClose = { viewModel.stopNearby(); showNearby = false })
    }
    if (nearbyDenied) {
        AlertDialog(
            onDismissRequest = { nearbyDenied = false },
            text = { Text(stringResource(R.string.sync_nearby_permissions)) },
            confirmButton = { TextButton(onClick = { nearbyDenied = false }) { Text(stringResource(R.string.summary_ok)) } },
        )
    }
}

@Composable
fun FriendsContent(
    state: FriendsUiState,
    onNickname: (String) -> Unit = {},
    onBoard: (Board) -> Unit = {},
    onScan: () -> Unit = {},
    onPaste: (String) -> Unit = {},
    onShareInvite: (String) -> Unit = {},
    onSend: (Friend) -> Unit = {},
    onRemove: (Friend) -> Unit = {},
    onNearby: () -> Unit = {},
    onImport: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    initiallyShowCode: Boolean = false,
) {
    var editingNickname by remember { mutableStateOf(false) }
    var showCode by remember { mutableStateOf(initiallyShowCode) }
    var showPaste by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.friends_title), style = MaterialTheme.typography.titleLarge)

        if (state.nickname == null) {
            NicknameCard(initial = "", onSave = onNickname, title = stringResource(R.string.friends_pick_nickname_title))
            return@Column
        }

        ProfileCard(
            state = state,
            onEdit = { editingNickname = true },
            onShowCode = { showCode = true },
            onScan = onScan,
            onPaste = { showPaste = true },
        )

        LeaderboardCard(state, onBoard)

        if (state.friends.isNotEmpty()) {
            Text(stringResource(R.string.friends_list), style = MaterialTheme.typography.titleMedium)
            state.friends.forEach { FriendCard(it, onSend, onRemove) }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sync_text), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onNearby, enabled = state.friends.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sync_nearby)) }
                    OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sync_import)) }
                }
            }
        }
    }

    if (editingNickname) {
        AlertDialog(
            onDismissRequest = { editingNickname = false },
            text = { NicknameCard(state.nickname.orEmpty(), { onNickname(it); editingNickname = false }, stringResource(R.string.friends_edit_nickname)) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editingNickname = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val invite = state.invite
    if (showCode && invite != null) {
        AlertDialog(
            onDismissRequest = { showCode = false },
            title = { Text(stringResource(R.string.friends_my_code)) },
            text = { MyCodeContent(invite, state.nickname.orEmpty(), state.fingerprint) },
            confirmButton = { TextButton(onClick = { onShareInvite(invite) }) { Text(stringResource(R.string.friends_share_invite)) } },
            dismissButton = { TextButton(onClick = { showCode = false }) { Text(stringResource(R.string.sync_done)) } },
        )
    }
    if (showPaste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPaste = false },
            title = { Text(stringResource(R.string.friends_paste)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.friends_paste_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { showPaste = false; onPaste(text) }) { Text(stringResource(R.string.friends_add)) } },
            dismissButton = { TextButton(onClick = { showPaste = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onMessageShown,
            text = {
                Text(
                    when (message) {
                        is FriendsMessage.Added -> stringResource(R.string.friends_added, message.nickname)
                        is FriendsMessage.Already -> stringResource(R.string.friends_already, message.nickname)
                        FriendsMessage.Invalid -> stringResource(R.string.friends_invalid)
                        FriendsMessage.Self -> stringResource(R.string.friends_self)
                        FriendsMessage.ScanFailed -> stringResource(R.string.friends_scan_failed)
                    },
                )
            },
            confirmButton = {
                if (message is FriendsMessage.Added) {
                    TextButton(onClick = { onMessageShown(); showCode = true }) { Text(stringResource(R.string.friends_my_code)) }
                } else {
                    TextButton(onClick = onMessageShown) { Text(stringResource(R.string.summary_ok)) }
                }
            },
            dismissButton = if (message is FriendsMessage.Added) ({ TextButton(onClick = onMessageShown) { Text(stringResource(R.string.summary_ok)) } }) else null,
        )
    }
}

/** QR con la invitación, apodo y huella para comparar en persona. */
@Composable
fun MyCodeContent(invite: String, nickname: String, fingerprint: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QrCode(invite, stringResource(R.string.friends_my_code), Modifier.fillMaxWidth())
        Text(nickname, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.friends_fingerprint, fingerprint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.friends_my_code_text), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NicknameCard(initial: String, onSave: (String) -> Unit, title: String) {
    var text by remember(initial) { mutableStateOf(initial) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.friends_pick_nickname_text), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                label = { Text(stringResource(R.string.friends_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun ProfileCard(state: FriendsUiState, onEdit: () -> Unit, onShowCode: () -> Unit, onScan: () -> Unit, onPaste: () -> Unit) {
    var addMenu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(state.nickname.orEmpty(), state.rows.firstOrNull { it.isMe }?.id.orEmpty())
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(state.nickname.orEmpty(), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.friends_fingerprint, state.fingerprint), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.friends_edit_nickname)) }
            }
            Text(stringResource(R.string.friends_fingerprint_hint), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowCode, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.friends_my_code)) }
                Box(Modifier.weight(1f)) {
                    Button(onClick = { addMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.friends_add)) }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.friends_scan)) }, onClick = { addMenu = false; onScan() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.friends_paste)) }, onClick = { addMenu = false; onPaste() })
                    }
                }
            }
        }
    }
}

@Composable
private fun LeaderboardCard(state: FriendsUiState, onBoard: (Board) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.board_title), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = state.board == Board.WEEK, onClick = { onBoard(Board.WEEK) }, label = { Text(stringResource(R.string.board_week)) })
                    FilterChip(selected = state.board == Board.TOTAL, onClick = { onBoard(Board.TOTAL) }, label = { Text(stringResource(R.string.board_total)) })
                }
            }
            if (state.friends.isEmpty()) {
                Text(stringResource(R.string.board_empty), style = MaterialTheme.typography.bodySmall)
            }
            state.rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}" },
                        modifier = Modifier.width(32.dp),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Avatar(row.nickname, row.id, small = true)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            if (row.isMe) stringResource(R.string.board_you, row.nickname) else row.nickname,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (row.isMe) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(stringResource(R.string.board_meta, row.level, row.streak), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        stringResource(R.string.board_points, if (state.board == Board.WEEK) row.weekPoints else row.totalPoints),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendCard(friend: Friend, onSend: (Friend) -> Unit, onRemove: (Friend) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(friend.nickname, friend.entity.authorId, small = true)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(friend.nickname, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (friend.entity.lastSyncEpochMs > 0) {
                            stringResource(R.string.friends_last_sync, Format.day(Instant.ofEpochMilli(friend.entity.lastSyncEpochMs).atZone(ZoneId.systemDefault()).toLocalDate()))
                        } else {
                            stringResource(R.string.friends_never_synced)
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSend(friend) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.friends_send)) }
                TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.friends_remove)) }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.friends_remove_confirm, friend.nickname)) },
            confirmButton = { TextButton(onClick = { confirm = false; onRemove(friend) }) { Text(stringResource(R.string.friends_remove)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private val avatarColors = listOf(
    Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF5C6BC0), Color(0xFF29B6F6),
    Color(0xFF26A69A), Color(0xFF9CCC65), Color(0xFFFFCA28), Color(0xFFFF7043),
)

/** Inicial del apodo sobre un color estable derivado de la identidad. */
@Composable
fun Avatar(nickname: String, id: String, small: Boolean = false) {
    val color = avatarColors[(id.hashCode() and Int.MAX_VALUE) % avatarColors.size]
    Box(Modifier.size(if (small) 36.dp else 48.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            nickname.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            style = if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NearbyDialog(status: NearbySync.Status, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.sync_nearby_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    status.failed -> Text(stringResource(R.string.sync_nearby_failed))
                    status.active -> Text(stringResource(R.string.sync_nearby_text))
                    else -> Text(stringResource(R.string.sync_nearby_stopped))
                }
                if (status.nearby.isNotEmpty()) Text(stringResource(R.string.sync_nearby_found, status.nearby.joinToString()))
                if (status.synced.isNotEmpty()) {
                    Text("✅ " + stringResource(R.string.sync_nearby_synced, status.synced.joinToString()), style = MaterialTheme.typography.titleSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(if (status.active) R.string.sync_stop else R.string.sync_done)) } },
    )
}

/** Permisos que pide Nearby Connections según la versión de Android. */
private fun nearbyPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.NEARBY_WIFI_DEVICES,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
    )
    else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun scanQr(context: Context, onResult: (String) -> Unit, onError: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options).startScan()
        .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
        .addOnFailureListener { onError() }
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null))
}

private fun shareFile(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType(FilePacketTransport.MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, title))
}
