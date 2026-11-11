package io.gymtracker.app.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.app.timer.RestTimer
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.WorkoutDraft
import io.gymtracker.sync.WorkoutRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerUi(val remainingSeconds: Int, val totalSeconds: Int) {
    val progress: Float get() = if (totalSeconds == 0) 0f else remainingSeconds / totalSeconds.toFloat()
}

data class WorkoutUiState(
    val draft: WorkoutDraft? = null,
    val exercises: Map<ExerciseId, ExerciseDefinition> = emptyMap(),
    val elapsedSeconds: Long = 0,
    val timer: TimerUi? = null,
    val restSeconds: Int = 90,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val timer: RestTimer,
) : ViewModel() {

    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(500) } }

    val state: StateFlow<WorkoutUiState> = combine(repository.draft, repository.observeState(), timer.state, ticker) { draft, training, running, now ->
        WorkoutUiState(
            draft = draft,
            exercises = training.exercises,
            elapsedSeconds = draft?.let { (now - it.startedAtEpochMs) / 1000 } ?: 0,
            timer = running?.let { TimerUi(((it.remainingMs() + 999) / 1000).toInt(), it.totalSeconds) },
            restSeconds = timer.defaultSeconds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState(restSeconds = timer.defaultSeconds))

    fun updateSet(entryIndex: Int, setIndex: Int, transform: (DraftSet) -> DraftSet) = edit { d ->
        d.mapSet(entryIndex, setIndex, transform)
    }

    /** Marca o desmarca una serie. Al marcar una serie de trabajo arranca el descanso. */
    fun toggleDone(entryIndex: Int, setIndex: Int) {
        val set = state.value.draft?.entries?.getOrNull(entryIndex)?.sets?.getOrNull(setIndex) ?: return
        val nowDone = !set.done
        edit { d -> d.mapSet(entryIndex, setIndex) { it.copy(done = nowDone) } }
        if (nowDone && !set.isWarmup) timer.start()
    }

    fun addSet(entryIndex: Int) = edit { d ->
        val entry = d.entries[entryIndex]
        val last = entry.sets.lastOrNull()?.copy(done = false) ?: DraftSet()
        d.copy(entries = d.entries.toMutableList().also { it[entryIndex] = entry.copy(sets = entry.sets + last) })
    }

    fun removeSet(entryIndex: Int, setIndex: Int) = edit { d ->
        val entry = d.entries[entryIndex]
        val sets = entry.sets.toMutableList().also { it.removeAt(setIndex) }
        d.copy(entries = d.entries.toMutableList().also { it[entryIndex] = entry.copy(sets = sets) })
    }

    fun removeEntry(entryIndex: Int) = edit { d -> d.copy(entries = d.entries.toMutableList().also { it.removeAt(entryIndex) }) }

    fun finish(onFinished: (saved: Boolean) -> Unit) {
        viewModelScope.launch {
            val session = repository.finishWorkout()
            if (session != null) timer.skip()
            onFinished(session != null)
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.discardWorkout()
            timer.skip()
            onDone()
        }
    }

    fun timerAdd() = timer.add(RestTimer_ADD)
    fun timerSkip() = timer.skip()
    fun setRestSeconds(seconds: Int) { timer.defaultSeconds = seconds }

    private fun edit(transform: (WorkoutDraft) -> WorkoutDraft) {
        viewModelScope.launch { repository.updateDraft(transform) }
    }

    private fun WorkoutDraft.mapSet(entryIndex: Int, setIndex: Int, transform: (DraftSet) -> DraftSet): WorkoutDraft {
        val entry = entries.getOrNull(entryIndex) ?: return this
        if (setIndex !in entry.sets.indices) return this
        val sets = entry.sets.toMutableList().also { it[setIndex] = transform(it[setIndex]) }
        return copy(entries = entries.toMutableList().also { it[entryIndex] = entry.copy(sets = sets) })
    }

    private companion object {
        const val RestTimer_ADD = 30
    }
}
