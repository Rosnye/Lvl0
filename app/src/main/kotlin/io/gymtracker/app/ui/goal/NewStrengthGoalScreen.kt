package io.gymtracker.app.ui.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.NumberField
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.sync.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Meta de repeticiones: hacer al menos [minReps] repeticiones en una serie de un ejercicio. */
data class NewGoalState(
    val exercise: ExerciseDefinition? = null,
    val repsText: String = "15",
    /** Mejor serie registrada del ejercicio elegido ("12 reps"). */
    val currentBest: String? = null,
) {
    val minReps: Int get() = repsText.toIntOrNull() ?: 0
    val canSave: Boolean get() = exercise != null && minReps in 2..999
}

@HiltViewModel
class NewStrengthGoalViewModel @Inject constructor(private val repository: WorkoutRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewGoalState())
    val state: StateFlow<NewGoalState> = _state

    fun setExercise(id: ExerciseId) {
        viewModelScope.launch {
            val training = repository.currentState()
            val best = training.sessions.flatMap { s -> s.entries.filter { it.exerciseId == id }.flatMap { it.sets } }
                .filter { it.isWorkingSet }
                .maxOfOrNull { it.reps }
            _state.update { it.copy(exercise = training.exercises[id], currentBest = best?.takeIf { b -> b > 0 }?.let { b -> "$b reps" }) }
        }
    }

    fun setReps(text: String) = _state.update { it.copy(repsText = text) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val exercise = s.exercise ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            repository.createStrengthGoal(exercise.id, targetWeightGrams = 0, minReps = s.minReps)
            onDone()
        }
    }
}

@Composable
fun NewStrengthGoalScreen(
    pickedExercise: ExerciseId?,
    onPickedConsumed: () -> Unit,
    onChooseExercise: () -> Unit,
    onClose: () -> Unit,
    viewModel: NewStrengthGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(pickedExercise) {
        if (pickedExercise != null) {
            viewModel.setExercise(pickedExercise)
            onPickedConsumed()
        }
    }
    NewStrengthGoalContent(state, onClose, onChooseExercise, viewModel::setReps) { viewModel.save(onClose) }
}

@Composable
fun NewStrengthGoalContent(
    state: NewGoalState,
    onBack: () -> Unit = {},
    onChooseExercise: () -> Unit = {},
    onReps: (String) -> Unit = {},
    onSave: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(stringResource(R.string.goal_new_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.goal_exercise), style = MaterialTheme.typography.labelLarge)
            OutlinedButton(onClick = onChooseExercise, modifier = Modifier.fillMaxWidth()) {
                Text(state.exercise?.name ?: stringResource(R.string.goal_choose_exercise))
            }
            state.currentBest?.let { Text(stringResource(R.string.goal_current_best, it), style = MaterialTheme.typography.bodySmall) }
            NumberField(state.repsText, stringResource(R.string.goal_min_reps), Modifier.fillMaxWidth(), onChange = onReps)
            Text(stringResource(R.string.goal_target_kg_hint), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.goal_save)) }
        }
    }
}
