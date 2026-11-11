package io.gymtracker.app.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.domain.model.Equipment
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.MuscleGroup
import io.gymtracker.sync.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer
import javax.inject.Inject

data class PickerUiState(
    val query: String = "",
    val muscle: MuscleGroup? = null,
    val results: List<ExerciseDefinition> = emptyList(),
)

@HiltViewModel
class ExercisePickerViewModel @Inject constructor(private val repository: WorkoutRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val muscle = MutableStateFlow<MuscleGroup?>(null)

    val state: StateFlow<PickerUiState> = combine(repository.observeState(), query, muscle) { training, q, m ->
        val needle = q.normalized()
        val results = training.exercises.values
            .filter { m == null || it.primaryMuscle == m }
            .filter { needle.isBlank() || it.name.normalized().contains(needle) }
            .sortedBy { it.name }
        PickerUiState(q, m, results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState())

    fun setQuery(value: String) { query.value = value }
    fun setMuscle(value: MuscleGroup?) { muscle.value = value }

    fun pick(id: ExerciseId, onPicked: () -> Unit) {
        viewModelScope.launch {
            repository.addExercise(id, repository.lastSetsOf(id))
            onPicked()
        }
    }

    fun create(name: String, muscle: MuscleGroup, equipment: Equipment, loadType: LoadType, onPicked: () -> Unit) {
        viewModelScope.launch {
            val exercise = repository.createCustomExercise(name, muscle, equipment, loadType)
            repository.addExercise(exercise.id)
            onPicked()
        }
    }

    private fun String.normalized(): String =
        Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
