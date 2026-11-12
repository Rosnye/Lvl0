package io.gymtracker.domain.goals

import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalSpec
import io.gymtracker.domain.model.Weeks
import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoreReport
import io.gymtracker.domain.scoring.ScoringEngine
import java.time.LocalDate

/** Meta de frecuencia vigente esta semana (la del usuario o la de defecto). */
data class FrequencyProgress(
    val target: Int,
    val thisWeek: Int,
    val streakWeeks: Int,
    val weekStart: LocalDate,
    /** Meta explícita del usuario; null si se usa la de defecto. */
    val goal: Goal?,
) {
    val reached: Boolean get() = thisWeek >= target
    val fraction: Float get() = (thisWeek.toFloat() / target).coerceIn(0f, 1f)
}

/**
 * Progreso de una meta de fuerza. Con peso objetivo 0 es una meta de repeticiones
 * (p. ej. 15 dominadas): se mide la mejor cantidad de reps.
 */
data class StrengthProgress(
    val goal: Goal,
    val exerciseId: ExerciseId,
    val targetWeightGrams: Int,
    val minReps: Int,
    /** Mejor peso con al menos minReps desde que se creó la meta (gramos). */
    val bestWeightGrams: Int,
    /** Mejor número de reps en una serie desde que se creó la meta. */
    val bestReps: Int,
    val achievedOn: LocalDate?,
) {
    val isRepsGoal: Boolean get() = targetWeightGrams == 0
    val achieved: Boolean get() = achievedOn != null
    val fraction: Float
        get() = when {
            achieved -> 1f
            isRepsGoal -> (bestReps.toFloat() / minReps).coerceIn(0f, 1f)
            else -> (bestWeightGrams.toFloat() / targetWeightGrams).coerceIn(0f, 1f)
        }
}

data class GoalsOverview(
    val frequency: FrequencyProgress,
    val activeStrength: List<StrengthProgress>,
    val achievedStrength: List<StrengthProgress>,
)

object GoalTracker {
    private val engine = ScoringEngine()

    fun overview(state: TrainingState, report: ScoreReport, today: LocalDate): GoalsOverview {
        val week = Weeks.startOf(today)
        val currentFrequencyGoal = state.goals
            .filter { it.spec is GoalSpec.WeeklyFrequency && it.createdDate <= today }
            .sortedBy { it.createdDate }
            .lastOrNull()
        val frequency = FrequencyProgress(
            target = engine.weeklyTargetFor(today, state.goals),
            thisWeek = report.sessionsByWeek[week] ?: 0,
            streakWeeks = report.currentStreakWeeks,
            weekStart = week,
            goal = currentFrequencyGoal,
        )
        val strength = state.goals.mapNotNull { goal ->
            val spec = goal.spec as? GoalSpec.Strength ?: return@mapNotNull null
            strengthProgress(state, goal, spec)
        }
        return GoalsOverview(
            frequency = frequency,
            activeStrength = strength.filter { !it.achieved },
            achievedStrength = strength.filter { it.achieved }.sortedByDescending { it.achievedOn },
        )
    }

    private fun strengthProgress(state: TrainingState, goal: Goal, spec: GoalSpec.Strength): StrengthProgress {
        var bestWeight = 0
        var bestReps = 0
        var achievedOn: LocalDate? = null
        val sessions = state.sessions
            .filter { it.date >= goal.createdDate }
            .sortedWith(compareBy({ it.date }, { it.startedAtEpochMs }, { it.id.value }))
        for (session in sessions) {
            for (entry in session.entries) {
                if (entry.exerciseId != spec.exerciseId) continue
                for (set in entry.sets) {
                    if (!set.isWorkingSet) continue
                    bestReps = maxOf(bestReps, set.reps)
                    if (set.reps >= spec.minReps) bestWeight = maxOf(bestWeight, set.weightGrams)
                }
            }
            if (achievedOn == null && engine.achieves(session, spec)) achievedOn = session.date
        }
        return StrengthProgress(goal, spec.exerciseId, spec.targetWeightGrams, spec.minReps, bestWeight, bestReps, achievedOn)
    }
}
