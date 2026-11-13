package io.gymtracker.app.ui.routine

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.Routine
import io.gymtracker.domain.model.RoutineExercise
import io.gymtracker.domain.model.RoutineId
import io.gymtracker.sync.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineEditorState(
    val routine: Routine? = null,
    val isNew: Boolean = true,
    val exercises: Map<ExerciseId, ExerciseDefinition> = emptyMap(),
) {
    val canSave: Boolean get() = routine != null && routine.name.isNotBlank() && routine.exercises.isNotEmpty()
}

/** Edita una rutina en memoria; solo se registra el evento al guardar. */
@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(RoutineEditorState())
    val state: StateFlow<RoutineEditorState> = _state

    init {
        val idArg = savedStateHandle.get<String>(ARG_ID)
        viewModelScope.launch {
            val training = repository.currentState()
            val existing = training.routines.firstOrNull { it.id.value == idArg }
            _state.value = RoutineEditorState(
                routine = existing ?: Routine(repository.newRoutineId(), "", emptyList()),
                isNew = existing == null,
                exercises = training.exercises,
            )
        }
    }

    fun rename(name: String) = edit { it.copy(name = name.take(60)) }

    fun addExercise(id: ExerciseId) {
        viewModelScope.launch {
            val plan = repository.planFor(id)
            edit { it.copy(exercises = it.exercises + RoutineExercise(id, plan)) }
        }
    }

    fun setPlan(exerciseIndex: Int, sets: List<DraftSet>) = editExercise(exerciseIndex) { e -> e.copy(sets = sets.map { it.copy(done = false) }) }

    fun removeExercise(exerciseIndex: Int) = edit { r ->
        r.copy(exercises = r.exercises.toMutableList().also { if (exerciseIndex in it.indices) it.removeAt(exerciseIndex) })
    }

    fun save(onDone: () -> Unit) {
        val routine = _state.value.routine ?: return
        if (!_state.value.canSave) return
        viewModelScope.launch {
            repository.saveRoutine(routine)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val routine = _state.value.routine ?: return
        viewModelScope.launch {
            repository.deleteRoutine(routine.id)
            onDone()
        }
    }

    private fun edit(transform: (Routine) -> Routine) = _state.update { s -> s.copy(routine = s.routine?.let(transform)) }

    private fun editExercise(index: Int, transform: (RoutineExercise) -> RoutineExercise) = edit { r ->
        if (index !in r.exercises.indices) r else r.copy(exercises = r.exercises.toMutableList().also { it[index] = transform(it[index]) })
    }

    companion object {
        const val ARG_ID = "id"
        const val NEW = "new"
    }
}

