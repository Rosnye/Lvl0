package io.gymtracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.domain.model.WorkoutDraft
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.scoring.ScoringEngine
import io.gymtracker.domain.scoring.SessionScore
import io.gymtracker.sync.LocalAuthor
import io.gymtracker.sync.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RecentSession(val session: WorkoutSession, val score: SessionScore?)

data class HomeUiState(
    val fingerprint: String = "",
    val totalPoints: Int = 0,
    val weekPoints: Int = 0,
    val level: Int = 1,
    val streakWeeks: Int = 0,
    val sessionCount: Int = 0,
    val draft: WorkoutDraft? = null,
    val recent: List<RecentSession> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    localAuthor: LocalAuthor,
    private val repository: WorkoutRepository,
    scoringEngine: ScoringEngine,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(repository.observeState(), repository.draft) { training, draft ->
        val today = LocalDate.now()
        val report = scoringEngine.compute(training, today)
        val summary = report.summaryFor(today)
        val scores = report.sessionScores.associateBy { it.sessionId }
        HomeUiState(
            fingerprint = localAuthor.authorId.fingerprint,
            totalPoints = report.totalPoints,
            weekPoints = summary.weekPoints,
            level = report.level,
            streakWeeks = report.currentStreakWeeks,
            sessionCount = training.sessions.size,
            draft = draft,
            recent = training.sessions.asReversed().take(3).map { RecentSession(it, scores[it.id]) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(fingerprint = localAuthor.authorId.fingerprint))

    fun startWorkout(onStarted: () -> Unit) {
        viewModelScope.launch {
            repository.startWorkout()
            onStarted()
        }
    }
}
