package io.gymtracker.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.domain.achievements.Achievement
import io.gymtracker.domain.achievements.AchievementEngine
import io.gymtracker.domain.goals.GoalTracker
import io.gymtracker.domain.goals.GoalsOverview
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.scoring.ScoringEngine
import io.gymtracker.sync.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthCalendar(
    val month: YearMonth,
    /** Días del mes con al menos una sesión registrada. */
    val trainedDays: Set<LocalDate>,
    val today: LocalDate,
) {
    val sessionDays: Int get() = trainedDays.size
}

data class ProgressUiState(
    val level: Int = 1,
    val totalPoints: Int = 0,
    val levelStartPoints: Int = 0,
    val nextLevelPoints: Int = 500,
    val streakWeeks: Int = 0,
    val calendar: MonthCalendar = MonthCalendar(YearMonth.now(), emptySet(), LocalDate.now()),
    val monthSessions: Int = 0,
    val goals: GoalsOverview? = null,
    val achievements: List<Achievement> = emptyList(),
    val exercises: Map<ExerciseId, ExerciseDefinition> = emptyMap(),
) {
    val levelFraction: Float
        get() = ((totalPoints - levelStartPoints).toFloat() / (nextLevelPoints - levelStartPoints)).coerceIn(0f, 1f)
}

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val scoringEngine: ScoringEngine,
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<ProgressUiState> = combine(repository.observeState(), month) { training, ym ->
        val today = LocalDate.now()
        val report = scoringEngine.compute(training, today)
        val monthSessions = training.sessions.filter { YearMonth.from(it.date) == ym }
        ProgressUiState(
            level = report.level,
            totalPoints = report.totalPoints,
            levelStartPoints = ScoringEngine.pointsForLevel(report.level),
            nextLevelPoints = ScoringEngine.pointsForLevel(report.level + 1),
            streakWeeks = report.currentStreakWeeks,
            calendar = MonthCalendar(ym, monthSessions.map { it.date }.toSet(), today),
            monthSessions = monthSessions.size,
            goals = GoalTracker.overview(training, report, today),
            achievements = AchievementEngine.evaluate(training, report),
            exercises = training.exercises,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { if (month.value < YearMonth.now()) month.value = month.value.plusMonths(1) }

    fun setWeeklyFrequency(sessions: Int) {
        viewModelScope.launch { repository.setWeeklyFrequency(sessions) }
    }

    fun deleteGoal(id: GoalId) {
        val stillActive = state.value.goals?.activeStrength?.any { it.goal.id == id } == true
        if (!stillActive) return // las metas cumplidas no se archivan: conservan sus puntos
        viewModelScope.launch { repository.archiveGoal(id) }
    }
}
