package io.gymtracker.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.Format
import io.gymtracker.domain.model.SessionId

@Composable
fun HomeScreen(
    onOpenWorkout: () -> Unit,
    onOpenSession: (SessionId) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onStart = { viewModel.startWorkout(onOpenWorkout) },
        onOpenSession = onOpenSession,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onStart: () -> Unit = {},
    onOpenSession: (SessionId) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            val draft = state.draft
            if (draft == null) {
                Text(stringResource(R.string.home_start_workout))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.home_resume_workout))
                    Text(
                        stringResource(R.string.home_resume_detail, draft.doneSetCount, Format.time(draft.startedAtEpochMs)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat(stringResource(R.string.home_total_points), state.totalPoints.toString(), Modifier.weight(1f))
            Stat(stringResource(R.string.home_week_points), state.weekPoints.toString(), Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.home_level, state.level), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.home_streak, state.streakWeeks))
            Text(stringResource(R.string.home_sessions, state.sessionCount), style = MaterialTheme.typography.bodyMedium)
        }

        Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
        if (state.recent.isEmpty()) {
            Text(stringResource(R.string.home_no_sessions), style = MaterialTheme.typography.bodyMedium)
        }
        state.recent.forEach { item ->
            Card(Modifier.fillMaxWidth().clickable { onOpenSession(item.session.id) }) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(Format.day(item.session.date), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(
                                R.string.history_summary,
                                item.session.entries.size,
                                item.session.workingSetCount,
                                Format.duration(item.session.durationSeconds),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(stringResource(R.string.history_points, item.score?.points ?: 0), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_identity), style = MaterialTheme.typography.labelLarge)
                Text(state.fingerprint, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_identity_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
