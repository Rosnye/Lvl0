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
    /** Clave estable de cada ejercicio de la rutina (en su orden), para arrastrar y animar. */
    val keys: List<Long> = emptyList(),
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
    private var nextKey = 0L

    init {
        val idArg = savedStateHandle.get<String>(ARG_ID)
        viewModelScope.launch {
            val training = repository.currentState()
            val existing = training.routines.firstOrNull { it.id.value == idArg }
            val routine = existing ?: Routine(repository.newRoutineId(), "", emptyList())
            _state.value = RoutineEditorState(
                routine = routine,
                isNew = existing == null,
                exercises = training.exercises,
                keys = routine.exercises.map { nextKey++ },
            )
        }
    }

    fun rename(name: String) = edit { it.copy(name = name.take(60)) }

    fun addExercise(id: ExerciseId) {
        viewModelScope.launch {
            val plan = repository.planFor(id)
            _state.update { s ->
                val r = s.routine ?: return@update s
                s.copy(routine = r.copy(exercises = r.exercises + RoutineExercise(id, plan)), keys = s.keys + nextKey++)
            }
        }
    }

    fun setPlan(exerciseIndex: Int, sets: List<DraftSet>) = editExercise(exerciseIndex) { e -> e.copy(sets = sets.map { it.copy(done = false) }) }

    fun removeExercise(exerciseIndex: Int) = _state.update { s ->
        val r = s.routine ?: return@update s
        if (exerciseIndex !in r.exercises.indices) return@update s
        s.copy(
            routine = r.copy(exercises = r.exercises.toMutableList().also { it.removeAt(exerciseIndex) }),
            keys = s.keys.toMutableList().also { it.removeAt(exerciseIndex) },
        )
    }

    /** Mueve un ejercicio a la posición de otro (arrastrar para ordenar), identificados por su clave. */
    fun move(fromKey: Long, toKey: Long) = _state.update { s ->
        val r = s.routine ?: return@update s
        val from = s.keys.indexOf(fromKey)
        val to = s.keys.indexOf(toKey)
        if (from < 0 || to < 0 || from == to) return@update s
        val exercises = r.exercises.toMutableList().apply { add(to, removeAt(from)) }
        val keys = s.keys.toMutableList().apply { add(to, removeAt(from)) }
        s.copy(routine = r.copy(exercises = exercises), keys = keys)
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

