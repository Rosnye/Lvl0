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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.FinishSummary
import io.gymtracker.app.ui.common.Format
import io.gymtracker.app.ui.common.emoji
import io.gymtracker.app.ui.common.titleRes
import io.gymtracker.domain.model.Routine
import io.gymtracker.domain.model.RoutineId
import io.gymtracker.domain.model.SessionId

@Composable
fun HomeScreen(
    onOpenWorkout: () -> Unit,
    onOpenSession: (SessionId) -> Unit,
    onEditRoutine: (RoutineId?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onStart = { routine -> viewModel.startWorkout(routine, onOpenWorkout) },
        onOpenSession = onOpenSession,
        onEditRoutine = onEditRoutine,
        onDismissSummary = viewModel::dismissSummary,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onStart: (Routine?) -> Unit = {},
    onOpenSession: (SessionId) -> Unit = {},
    onEditRoutine: (RoutineId?) -> Unit = {},
    onDismissSummary: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val draft = state.draft
        Button(onClick = { onStart(null) }, modifier = Modifier.fillMaxWidth()) {
            if (draft == null) {
                Text(stringResource(R.string.home_start_empty))
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.home_level, state.level), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.home_streak, state.streakWeeks), style = MaterialTheme.typography.bodyMedium)
                }
                Text(stringResource(R.string.goal_frequency_progress, state.weekSessions, state.weeklyTarget), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { (state.weekSessions.toFloat() / state.weeklyTarget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_routines), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onEditRoutine(null) }) { Text(stringResource(R.string.home_new_routine)) }
        }
        if (state.routines.isEmpty()) {
            Text(stringResource(R.string.home_no_routines), style = MaterialTheme.typography.bodyMedium)
        } else if (draft != null) {
            Text(stringResource(R.string.home_routine_busy), style = MaterialTheme.typography.bodySmall)
        }
        state.routines.forEach { routine ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(routine.name, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.home_routine_detail, routine.exercises.size, routine.setCount), style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onEditRoutine(routine.id) }) { Text(stringResource(R.string.home_routine_edit)) }
                    OutlinedButton(onClick = { onStart(routine) }, enabled = draft == null) { Text(stringResource(R.string.home_routine_start)) }
                }
            }
        }

        Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
        if (state.recent.isEmpty()) {
            Text(stringResource(R.string.home_no_sessions), style = MaterialTheme.typography.bodyMedium)
        }
        state.recent.forEach { item ->
            Card(Modifier.fillMaxWidth().clickable { onOpenSession(item.session.id) }) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.session.routineName ?: Format.day(item.session.date), style = MaterialTheme.typography.titleSmall)
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

    state.summary?.let { FinishSummaryDialog(it, onDismissSummary) }
}

@Composable
fun FinishSummaryDialog(summary: FinishSummary, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.summary_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.points != null) {
                    Text(stringResource(R.string.summary_points, summary.points), style = MaterialTheme.typography.headlineSmall)
                } else {
                    Text(stringResource(R.string.summary_not_scored))
                }
                if (summary.newBadges.isNotEmpty()) {
                    Text(stringResource(R.string.summary_new_badges), style = MaterialTheme.typography.titleSmall)
                    summary.newBadges.forEach { badge ->
                        Text("${badge.emoji()}  ${stringResource(badge.titleRes())}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.summary_ok)) } },
    )
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
