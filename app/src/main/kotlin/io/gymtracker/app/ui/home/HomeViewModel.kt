package io.gymtracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.gymtracker.domain.scoring.ScoringEngine
import io.gymtracker.sync.LocalAuthor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val fingerprint: String = "",
    val totalPoints: Int = 0,
    val weekPoints: Int = 0,
    val level: Int = 1,
    val streakWeeks: Int = 0,
    val sessionCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    localAuthor: LocalAuthor,
    scoringEngine: ScoringEngine,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = localAuthor.observeState()
        .map { training ->
            val today = LocalDate.now()
            val report = scoringEngine.compute(training, today)
            val summary = report.summaryFor(today)
            HomeUiState(
                fingerprint = localAuthor.authorId.fingerprint,
                totalPoints = report.totalPoints,
                weekPoints = summary.weekPoints,
                level = report.level,
                streakWeeks = report.currentStreakWeeks,
                sessionCount = training.sessions.size,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(fingerprint = localAuthor.authorId.fingerprint))
}
