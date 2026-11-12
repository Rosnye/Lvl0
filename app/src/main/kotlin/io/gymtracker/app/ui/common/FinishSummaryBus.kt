package io.gymtracker.app.ui.common

import io.gymtracker.domain.achievements.AchievementId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Resumen que se muestra en Inicio después de terminar un entrenamiento. */
data class FinishSummary(val points: Int?, val newBadges: List<AchievementId>)

@Singleton
class FinishSummaryBus @Inject constructor() {
    private val _summary = MutableStateFlow<FinishSummary?>(null)
    val summary: StateFlow<FinishSummary?> = _summary

    fun post(summary: FinishSummary) { _summary.value = summary }
    fun consume() { _summary.value = null }
}
