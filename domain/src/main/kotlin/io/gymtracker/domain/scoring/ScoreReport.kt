package io.gymtracker.domain.scoring

import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.ScoreSummary
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.Weeks
import java.time.LocalDate

/** Desglose del puntaje de una sesión, útil para explicarlo en pantalla. */
data class SessionScore(
    val sessionId: SessionId,
    val localDate: LocalDate,
    val basePoints: Int,
    val volumeKg: Double,
    val volumeBonus: Int,
    val prCount: Int,
    val prBonus: Int,
    val streakWeeks: Int,
    val multiplier: Double,
    val goalBonus: Int,
    /** Esta sesión completó la meta de frecuencia de su semana. */
    val weeklyGoalReached: Boolean = false,
    /** Metas de fuerza cumplidas por primera vez en esta sesión. */
    val strengthGoalsReached: Int = 0,
    val points: Int,
)

data class ScoreReport(
    val formulaVersion: Int,
    val sessionScores: List<SessionScore>,
    /** Lunes de la semana -> puntos. */
    val pointsByWeek: Map<LocalDate, Int>,
    val sessionsByWeek: Map<LocalDate, Int>,
    val totalPoints: Int,
    val currentStreakWeeks: Int,
    val level: Int,
    /** Mejor 1RM estimado (gramos) por ejercicio. */
    val bestE1rmGrams: Map<ExerciseId, Int>,
) {
    fun summaryFor(date: LocalDate): ScoreSummary {
        val week = Weeks.startOf(date)
        return ScoreSummary(
            formulaVersion = formulaVersion,
            weekStart = week.toString(),
            weekPoints = pointsByWeek[week] ?: 0,
            weekSessions = sessionsByWeek[week] ?: 0,
            totalPoints = totalPoints,
            streakWeeks = currentStreakWeeks,
            level = level,
        )
    }
}
