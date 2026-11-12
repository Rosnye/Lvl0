package io.gymtracker.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.Weeks
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.scoring.ScoringEngine
import io.gymtracker.domain.scoring.SessionScore
import io.gymtracker.sync.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SessionItem(val session: WorkoutSession, val score: SessionScore?, val volumeKg: Double)

data class WeekGroup(val weekStart: LocalDate, val items: List<SessionItem>) {
    val points: Int get() = items.sumOf { it.score?.points ?: 0 }
}

data class HistoryUiState(
    val weeks: List<WeekGroup> = emptyList(),
    val exercises: Map<ExerciseId, ExerciseDefinition> = emptyMap(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val scoringEngine: ScoringEngine,
) : ViewModel() {
    val state: StateFlow<HistoryUiState> = repository.observeState().map { training ->
        val report = scoringEngine.compute(training, LocalDate.now())
        val scores = report.sessionScores.associateBy { it.sessionId }
        val items = training.sessions.asReversed().map { s ->
            SessionItem(s, scores[s.id], scores[s.id]?.volumeKg ?: scoringEngine.volumeKg(s, training.exercises))
        }
        HistoryUiState(
            weeks = items.groupBy { Weeks.startOf(it.session.date) }.map { (week, list) -> WeekGroup(week, list) }.sortedByDescending { it.weekStart },
            exercises = training.exercises,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun delete(id: SessionId, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSession(id)
            onDeleted()
        }
    }

    fun saveAsRoutine(name: String, session: WorkoutSession, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.saveRoutineFromSession(name, session)
            onDone()
        }
    }

    fun item(id: SessionId): SessionItem? = state.value.weeks.asSequence().flatMap { it.items }.firstOrNull { it.session.id == id }
}
