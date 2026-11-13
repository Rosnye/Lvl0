package io.gymtracker.app.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.Format
import io.gymtracker.app.ui.common.SetPlanEditor
import io.gymtracker.domain.model.DraftEntry
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.LoadType

@Composable
fun WorkoutScreen(
    onAddExercise: () -> Unit,
    onClosed: () -> Unit,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationPermissionRequest()
    WorkoutContent(
        state = state,
        onAddExercise = onAddExercise,
        onSetPlan = viewModel::setPlan,
        onToggleDone = viewModel::toggleDone,
        onRemoveEntry = viewModel::removeEntry,
        onTimerAdd = viewModel::timerAdd,
        onTimerSkip = viewModel::timerSkip,
        onRestSeconds = viewModel::setRestSeconds,
        onFinish = { viewModel.finish { saved -> if (saved) onClosed() } },
        onDiscard = { viewModel.discard(onClosed) },
    )
}

@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun WorkoutContent(
    state: WorkoutUiState,
    onAddExercise: () -> Unit = {},
    onSetPlan: (Int, List<DraftSet>) -> Unit = { _, _ -> },
    onToggleDone: (Int, Int) -> Unit = { _, _ -> },
    onRemoveEntry: (Int) -> Unit = {},
    onTimerAdd: () -> Unit = {},
    onTimerSkip: () -> Unit = {},
    onRestSeconds: (Int) -> Unit = {},
    onFinish: () -> Unit = {},
    onDiscard: () -> Unit = {},
) {
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val draft = state.draft

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(stringResource(R.string.workout_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.workout_elapsed, Format.mmss(state.elapsedSeconds)), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = { confirmDiscard = true }) { Text(stringResource(R.string.workout_discard)) }
                Button(onClick = { confirmFinish = true }, enabled = (draft?.doneSetCount ?: 0) > 0) {
                    Text(stringResource(R.string.workout_finish))
                }
            }
        }

        RestTimerBar(state, onTimerAdd, onTimerSkip, onRestSeconds)

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (draft == null || draft.entries.isEmpty()) {
                item { Text(stringResource(R.string.workout_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            draft?.entries?.let { entries ->
                itemsIndexed(entries, key = { i, e -> "${e.exerciseId.value}-$i" }) { entryIndex, entry ->
                    ExerciseCard(
                        entry = entry,
                        exercise = state.exercises[entry.exerciseId],
                        onSetPlan = { sets -> onSetPlan(entryIndex, sets) },
                        onToggleDone = { setIndex -> onToggleDone(entryIndex, setIndex) },
                        onRemove = { onRemoveEntry(entryIndex) },
                    )
                }
            }
            item {
                OutlinedButton(onClick = onAddExercise, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.workout_add_exercise))
                }
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            text = { Text(stringResource(R.string.workout_finish_confirm)) },
            confirmButton = { TextButton(onClick = { confirmFinish = false; onFinish() }) { Text(stringResource(R.string.workout_finish)) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            text = { Text(stringResource(R.string.workout_discard_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDiscard() }) { Text(stringResource(R.string.workout_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RestTimerBar(state: WorkoutUiState, onAdd: () -> Unit, onSkip: () -> Unit, onRestSeconds: (Int) -> Unit) {
    val timer = state.timer
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (timer != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.timer_title), style = MaterialTheme.typography.labelMedium)
                        Text(Format.mmss(timer.remainingSeconds.toLong()), style = MaterialTheme.typography.displaySmall)
                    }
                    Row {
                        OutlinedButton(onClick = onAdd) { Text(stringResource(R.string.timer_add_30)) }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onSkip) { Text(stringResource(R.string.timer_skip)) }
                    }
                }
                LinearProgressIndicator(progress = { timer.progress }, modifier = Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.workout_rest_default, state.restSeconds), style = MaterialTheme.typography.labelLarge)
                    listOf(60, 90, 120, 180).forEach { s ->
                        FilterChip(selected = state.restSeconds == s, onClick = { onRestSeconds(s) }, label = { Text("${s}s") })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    entry: DraftEntry,
    exercise: ExerciseDefinition?,
    onSetPlan: (List<DraftSet>) -> Unit,
    onToggleDone: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val loadType = exercise?.loadType ?: LoadType.WEIGHTED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(exercise?.name ?: entry.exerciseId.value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workout_remove_exercise)) }
            }
            SetPlanEditor(entry.sets, loadType, onSetPlan)
            Text(stringResource(R.string.plan_tap_done), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entry.sets.forEachIndexed { index, set ->
                    FilledIconToggleButton(
                        checked = set.done,
                        onCheckedChange = { onToggleDone(index) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        if (set.done) {
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.workout_done_desc))
                        } else {
                            Text("${index + 1}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
