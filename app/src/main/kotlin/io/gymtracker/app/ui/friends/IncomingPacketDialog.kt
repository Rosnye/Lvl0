package io.gymtracker.app.ui.friends

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.app.R
import io.gymtracker.app.friends.IncomingPackets
import io.gymtracker.crypto.RejectReason
import io.gymtracker.sync.ReceiveResult
import javax.inject.Inject

@HiltViewModel
class IncomingViewModel @Inject constructor(private val incoming: IncomingPackets) : ViewModel() {
    val result = incoming.result
    fun consume() = incoming.consume()
}

/** Resultado de abrir un archivo de puntaje, venga de otra app o de "Importar archivo". */
@Composable
fun IncomingPacketDialog(onViewBoard: () -> Unit, viewModel: IncomingViewModel = hiltViewModel()) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val current = result ?: return
    val accepted = current as? ReceiveResult.Accepted
    AlertDialog(
        onDismissRequest = viewModel::consume,
        title = { Text(stringResource(if (accepted != null) R.string.incoming_title else R.string.incoming_error_title)) },
        text = {
            Text(
                when (current) {
                    is ReceiveResult.Accepted ->
                        if (current.newEvents > 0) stringResource(R.string.incoming_ok, current.fromNickname)
                        else stringResource(R.string.incoming_ok_none, current.fromNickname)
                    is ReceiveResult.Rejected -> stringResource(
                        when (current.reason) {
                            RejectReason.NOT_FOR_ME -> R.string.incoming_not_for_me
                            RejectReason.UNKNOWN_SENDER -> R.string.incoming_unknown
                            RejectReason.INVALID_EVENT, RejectReason.MULTIPLE_AUTHORS -> R.string.incoming_invalid
                            RejectReason.CORRUPT -> R.string.incoming_corrupt
                        },
                    )
                },
            )
        },
        confirmButton = {
            if (accepted != null) {
                TextButton(onClick = { viewModel.consume(); onViewBoard() }) { Text(stringResource(R.string.incoming_view_board)) }
            } else {
                TextButton(onClick = viewModel::consume) { Text(stringResource(R.string.summary_ok)) }
            }
        },
    )
}
