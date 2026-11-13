package io.gymtracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.app.ui.common.FinishSummary
import io.gymtracker.app.ui.common.FinishSummaryBus
import io.gymtracker.app.settings.ThemeMode
import io.gymtracker.app.settings.ThemeSettings
import io.gymtracker.domain.model.Routine
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
    val weekSessions: Int = 0,
    val weeklyTarget: Int = 3,
    val draft: WorkoutDraft? = null,
    val routines: List<Routine> = emptyList(),
    val recent: List<RecentSession> = emptyList(),
    val summary: FinishSummary? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    localAuthor: LocalAuthor,
    private val repository: WorkoutRepository,
    scoringEngine: ScoringEngine,
    private val summaryBus: FinishSummaryBus,
    private val themeSettings: ThemeSettings,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        repository.observeState(), repository.draft, summaryBus.summary, themeSettings.mode,
    ) { training, draft, summary, theme ->
        val today = LocalDate.now()
        val report = scoringEngine.compute(training, today)
        val week = report.summaryFor(today)
        val scores = report.sessionScores.associateBy { it.sessionId }
        HomeUiState(
            fingerprint = localAuthor.authorId.fingerprint,
            totalPoints = report.totalPoints,
            weekPoints = week.weekPoints,
            level = report.level,
            streakWeeks = report.currentStreakWeeks,
            sessionCount = training.sessions.size,
            weekSessions = week.weekSessions,
            weeklyTarget = scoringEngine.weeklyTargetFor(today, training.goals),
            draft = draft,
            routines = training.routines,
            recent = training.sessions.asReversed().take(3).map { RecentSession(it, scores[it.id]) },
            summary = summary,
            themeMode = theme,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(fingerprint = localAuthor.authorId.fingerprint))

    fun startWorkout(routine: Routine? = null, onStarted: () -> Unit) {
        viewModelScope.launch {
            repository.startWorkout(routine)
            onStarted()
        }
    }

    fun dismissSummary() = summaryBus.consume()

    fun setTheme(mode: ThemeMode) = themeSettings.set(mode)
}
