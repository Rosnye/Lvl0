package io.gymtracker.app.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.SetPlanEditor
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.LoadType

@Composable
fun RoutineEditorScreen(
    pickedExercise: ExerciseId?,
    onPickedConsumed: () -> Unit,
    onAddExercise: () -> Unit,
    onClose: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pickedExercise, state.routine != null) {
        if (pickedExercise != null && state.routine != null) {
            viewModel.addExercise(pickedExercise)
            onPickedConsumed()
        }
    }
    RoutineEditorContent(
        state = state,
        onBack = onClose,
        onRename = viewModel::rename,
        onAddExercise = onAddExercise,
        onSetPlan = viewModel::setPlan,
        onRemoveExercise = viewModel::removeExercise,
        onSave = { viewModel.save(onClose) },
        onDelete = { viewModel.delete(onClose) },
    )
}

@Composable
fun RoutineEditorContent(
    state: RoutineEditorState,
    onBack: () -> Unit = {},
    onRename: (String) -> Unit = {},
    onAddExercise: () -> Unit = {},
    onSetPlan: (Int, List<DraftSet>) -> Unit = { _, _ -> },
    onRemoveExercise: (Int) -> Unit = {},
    onSave: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val routine = state.routine
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(
                stringResource(if (state.isNew) R.string.routine_new_title else R.string.routine_edit_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (!state.isNew) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.padding(end = 8.dp)) { Text(stringResource(R.string.save)) }
        }
        if (routine == null) return@Column
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = routine.name,
                    onValueChange = onRename,
                    label = { Text(stringResource(R.string.routine_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (routine.exercises.isEmpty()) {
                item { Text(stringResource(R.string.routine_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            itemsIndexed(routine.exercises, key = { i, e -> "${e.exerciseId.value}-$i" }) { exerciseIndex, exercise ->
                val definition = state.exercises[exercise.exerciseId]
                val loadType = definition?.loadType ?: LoadType.WEIGHTED
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(definition?.name ?: exercise.exerciseId.value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onRemoveExercise(exerciseIndex) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.workout_remove_exercise))
                            }
                        }
                        SetPlanEditor(exercise.sets, loadType) { sets -> onSetPlan(exerciseIndex, sets) }
                    }
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
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.routine_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
