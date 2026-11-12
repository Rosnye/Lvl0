package io.gymtracker.app.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import io.gymtracker.app.ui.common.labelRes
import io.gymtracker.domain.model.BuiltInExercises
import io.gymtracker.domain.model.Equipment
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.MuscleGroup

/** Selector de ejercicios. Devuelve el elegido con [onPicked]; quien lo abrió decide qué hacer. */
@Composable
fun ExercisePickerScreen(onPicked: (ExerciseId) -> Unit, onBack: () -> Unit, viewModel: ExercisePickerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PickerContent(
        state = state,
        onBack = onBack,
        onQuery = viewModel::setQuery,
        onMuscle = viewModel::setMuscle,
        onPick = onPicked,
        onCreate = { name, muscle, equipment, load -> viewModel.create(name, muscle, equipment, load, onPicked) },
    )
}

@Composable
fun PickerContent(
    state: PickerUiState,
    onBack: () -> Unit = {},
    onQuery: (String) -> Unit = {},
    onMuscle: (MuscleGroup?) -> Unit = {},
    onPick: (ExerciseId) -> Unit = {},
    onCreate: (String, MuscleGroup, Equipment, LoadType) -> Unit = { _, _, _, _ -> },
) {
    var showCreate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) { Text(stringResource(R.string.picker_create)) }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.picker_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = state.muscle == null, onClick = { onMuscle(null) }, label = { Text(stringResource(R.string.picker_all)) }) }
            items(MuscleGroup.entries) { m ->
                FilterChip(selected = state.muscle == m, onClick = { onMuscle(if (state.muscle == m) null else m) }, label = { Text(stringResource(m.labelRes())) })
            }
        }
        if (state.results.isEmpty()) {
            Text(stringResource(R.string.picker_no_results), modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(state.results, key = { it.id.value }) { exercise ->
                ExerciseRow(exercise, onClick = { onPick(exercise.id) })
                HorizontalDivider()
            }
        }
    }
    if (showCreate) {
        CreateExerciseDialog(
            initialName = state.query,
            onDismiss = { showCreate = false },
            onCreate = { n, m, e, l -> showCreate = false; onCreate(n, m, e, l) },
        )
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseDefinition, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            val custom = if (BuiltInExercises.isBuiltIn(exercise.id)) "" else " · ${stringResource(R.string.picker_custom)}"
            Text(stringResource(exercise.primaryMuscle.labelRes()) + " · " + stringResource(exercise.equipment.labelRes()) + custom)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateExerciseDialog(initialName: String, onDismiss: () -> Unit, onCreate: (String, MuscleGroup, Equipment, LoadType) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var muscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var equipment by remember { mutableStateOf(Equipment.BARBELL) }
    var load by remember { mutableStateOf(LoadType.WEIGHTED) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.picker_name)) }, singleLine = true)
                EnumDropdown(stringResource(R.string.picker_muscle), MuscleGroup.entries, muscle, { stringResource(it.labelRes()) }) { muscle = it }
                EnumDropdown(stringResource(R.string.picker_equipment), Equipment.entries, equipment, { stringResource(it.labelRes()) }) { equipment = it }
                EnumDropdown(stringResource(R.string.picker_load), LoadType.entries, load, { stringResource(it.labelRes()) }) { load = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name, muscle, equipment, load) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(label: String, options: List<T>, selected: T, labelOf: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(labelOf(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
