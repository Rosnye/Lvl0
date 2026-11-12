package io.gymtracker.domain.achievements

import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoreReport
import io.gymtracker.domain.scoring.ScoringEngine
import java.time.LocalDate

/**
 * Insignias. Se calculan de forma determinista a partir del puntaje: mismo historial,
 * mismas insignias y mismas fechas. Son privadas (no se comparten con amigos).
 */
enum class AchievementId(val target: Int) {
    FIRST_SESSION(1),
    SESSIONS_10(10),
    SESSIONS_50(50),
    SESSIONS_100(100),
    FIRST_PR(1),
    PRS_10(10),
    WEEKLY_GOAL(1),
    STREAK_4(4),
    STREAK_12(12),
    STRENGTH_GOAL(1),
    VOLUME_10T(10_000),
    VOLUME_100T(100_000),
    LEVEL_5(5),
    LEVEL_10(10),
    LONG_SESSION(90),
}

data class Achievement(
    val id: AchievementId,
    /** Valor actual de la métrica (sesiones, récords, kg, nivel, minutos...). */
    val progress: Int,
    val unlockedOn: LocalDate?,
) {
    val target: Int get() = id.target
    val unlocked: Boolean get() = unlockedOn != null
    val fraction: Float get() = if (unlocked) 1f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

object AchievementEngine {
    /** Minutos que debe durar una sesión para la insignia "Maratón". */
    private const val LONG_SESSION_SECONDS = 90 * 60

    fun evaluate(state: TrainingState, report: ScoreReport): List<Achievement> {
        val unlocked = HashMap<AchievementId, LocalDate>()
        val progress = HashMap<AchievementId, Int>()
        val durations = state.sessions.associate { it.id to it.durationSeconds }

        var sessions = 0
        var prs = 0
        var weeklyGoals = 0
        var bestStreak = 0
        var strengthGoals = 0
        var volumeKg = 0.0
        var points = 0
        var longestMinutes = 0

        fun check(id: AchievementId, value: Int, date: LocalDate) {
            progress[id] = maxOf(progress[id] ?: 0, value)
            if (value >= id.target && id !in unlocked) unlocked[id] = date
        }

        for (score in report.sessionScores) {
            val date = score.localDate
            sessions++
            prs += score.prCount
            volumeKg += score.volumeKg
            points += score.points
            if (score.weeklyGoalReached) {
                weeklyGoals++
                bestStreak = maxOf(bestStreak, score.streakWeeks + 1)
            }
            strengthGoals += score.strengthGoalsReached
            longestMinutes = maxOf(longestMinutes, (durations[score.sessionId] ?: 0) / 60)
            val level = ScoringEngine.levelFor(points)

            check(AchievementId.FIRST_SESSION, sessions, date)
            check(AchievementId.SESSIONS_10, sessions, date)
            check(AchievementId.SESSIONS_50, sessions, date)
            check(AchievementId.SESSIONS_100, sessions, date)
            check(AchievementId.FIRST_PR, prs, date)
            check(AchievementId.PRS_10, prs, date)
            check(AchievementId.WEEKLY_GOAL, weeklyGoals, date)
            check(AchievementId.STREAK_4, bestStreak, date)
            check(AchievementId.STREAK_12, bestStreak, date)
            check(AchievementId.STRENGTH_GOAL, strengthGoals, date)
            check(AchievementId.VOLUME_10T, volumeKg.toInt(), date)
            check(AchievementId.VOLUME_100T, volumeKg.toInt(), date)
            check(AchievementId.LEVEL_5, level, date)
            check(AchievementId.LEVEL_10, level, date)
            check(AchievementId.LONG_SESSION, longestMinutes, date)
        }

        return AchievementId.entries.map { id ->
            Achievement(id, progress[id] ?: if (id == AchievementId.LEVEL_5 || id == AchievementId.LEVEL_10) 1 else 0, unlocked[id])
        }
    }

    fun unlockedIds(state: TrainingState, report: ScoreReport): Set<AchievementId> =
        evaluate(state, report).filter { it.unlocked }.map { it.id }.toSet()
}
