package io.gymtracker.domain.scoring

import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.model.GoalSpec
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.Weeks
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoreFormula as F
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Motor de puntaje v1. Función pura: mismo estado + misma fecha = mismo resultado,
 * en cualquier dispositivo. No usa el reloj ni ninguna fuente externa.
 */
class ScoringEngine {

    fun compute(state: TrainingState, today: LocalDate): ScoreReport {
        val sessions = state.sessions
            .filter { it.workingSetCount >= F.MIN_WORKING_SETS }
            .sortedWith(compareBy({ it.date }, { it.startedAtEpochMs }, { it.id.value }))

        val sessionsByWeek: Map<LocalDate, Int> = sessions.groupingBy { Weeks.startOf(it.date) }.eachCount()

        val bestReps = HashMap<ExerciseId, Int>()
        val priorSessionsPerExercise = HashMap<ExerciseId, Int>()
        val scoredDays = HashSet<LocalDate>()
        val volumeHistory = ArrayList<Pair<LocalDate, Double>>()
        val sessionsSoFarInWeek = HashMap<LocalDate, Int>()
        val weeklyBonusAwarded = HashSet<LocalDate>()
        val strengthGoalsAwarded = HashSet<GoalId>()
        val scores = ArrayList<SessionScore>(sessions.size)

        for (session in sessions) {
            val date = session.date
            val week = Weeks.startOf(date)
            val firstOfDay = scoredDays.add(date)

            // 1. Base
            val base = if (firstOfDay) F.BASE_POINTS else 0

            // 2. Volumen (repeticiones) relativo al propio promedio reciente
            val volume = volumeReps(session, state.exercises).toDouble()
            val windowStart = date.minusDays(F.VOLUME_WINDOW_DAYS)
            val baseline = volumeHistory.filter { (d, _) -> d >= windowStart && d < date }.map { it.second }
            val volumeBonus = if (firstOfDay && baseline.size >= F.VOLUME_MIN_HISTORY && baseline.average() > 0.0) {
                val ratio = volume / baseline.average()
                (F.VOLUME_BONUS_MAX * (ratio - 1.0)).roundToInt().coerceIn(0, F.VOLUME_BONUS_MAX)
            } else 0
            volumeHistory += date to volume

            // 3. Récords personales: más repeticiones totales en un ejercicio que nunca
            var prCount = 0
            for ((exerciseId, reps) in repsByExercise(session, state.exercises)) {
                val prior = priorSessionsPerExercise[exerciseId] ?: 0
                val best = bestReps[exerciseId] ?: 0
                if (reps > best) {
                    if (prior >= F.PR_MIN_PRIOR_SESSIONS) prCount++
                    bestReps[exerciseId] = reps
                }
            }
            session.entries.map { it.exerciseId }.distinct().forEach { priorSessionsPerExercise.merge(it, 1, Int::plus) }
            val prBonus = F.PR_POINTS * min(prCount, F.PR_MAX_PER_SESSION)

            // 4. Racha de semanas anteriores completas
            val streak = streakBefore(week, sessionsByWeek, state.goals)
            val multiplier = 1.0 + F.STREAK_STEP * min(streak, F.STREAK_MAX_WEEKS)

            // 5. Bonos de metas
            var goalBonus = 0
            val target = weeklyTargetFor(date, state.goals)
            val countInWeek = (sessionsSoFarInWeek[week] ?: 0) + 1
            sessionsSoFarInWeek[week] = countInWeek
            val weeklyReached = countInWeek == target && weeklyBonusAwarded.add(week)
            if (weeklyReached) goalBonus += F.WEEKLY_GOAL_BONUS
            var strengthReached = 0
            for (goal in state.goals) {
                val spec = goal.spec as? GoalSpec.Strength ?: continue
                if (goal.id in strengthGoalsAwarded || date < goal.createdDate) continue
                if (achieves(session, spec)) {
                    strengthGoalsAwarded += goal.id
                    goalBonus += F.STRENGTH_GOAL_BONUS
                    strengthReached++
                }
            }

            val points = ((base + volumeBonus + prBonus) * multiplier).roundToInt() + goalBonus
            scores += SessionScore(
                sessionId = session.id,
                localDate = date,
                basePoints = base,
                volumeReps = volume.toInt(),
                volumeBonus = volumeBonus,
                prCount = prCount,
                prBonus = prBonus,
                streakWeeks = streak,
                multiplier = multiplier,
                goalBonus = goalBonus,
                weeklyGoalReached = weeklyReached,
                strengthGoalsReached = strengthReached,
                points = points,
            )
        }

        val pointsByWeek = scores.groupBy { Weeks.startOf(it.localDate) }
            .mapValues { (_, s) -> s.sumOf { it.points } }
            .toSortedMap()
        val total = scores.sumOf { it.points }
        val currentWeek = Weeks.startOf(today)
        val thisWeekMet = (sessionsByWeek[currentWeek] ?: 0) >= weeklyTargetFor(today, state.goals)
        val currentStreak = streakBefore(currentWeek, sessionsByWeek, state.goals) + (if (thisWeekMet) 1 else 0)

        return ScoreReport(
            formulaVersion = F.VERSION,
            sessionScores = scores,
            pointsByWeek = pointsByWeek,
            sessionsByWeek = sessionsByWeek.toSortedMap(),
            totalPoints = total,
            currentStreakWeeks = currentStreak,
            level = levelFor(total),
            bestRepsByExercise = bestReps,
        )
    }

    /** Semanas consecutivas anteriores a [week] que alcanzaron su meta de frecuencia. */
    private fun streakBefore(week: LocalDate, sessionsByWeek: Map<LocalDate, Int>, goals: List<Goal>): Int {
        var streak = 0
        var w = week.minusWeeks(1)
        while ((sessionsByWeek[w] ?: 0) >= weeklyTargetFor(w, goals)) {
            streak++
            w = w.minusWeeks(1)
        }
        return streak
    }

    /**
     * Meta de frecuencia vigente en [date]: la última creada hasta esa fecha, o la de defecto.
     * [goals] llega en orden de creación; entre metas del mismo día manda la última.
     */
    fun weeklyTargetFor(date: LocalDate, goals: List<Goal>): Int {
        val goal = goals
            .filter { it.spec is GoalSpec.WeeklyFrequency && it.createdDate <= date }
            .sortedBy { it.createdDate }
            .lastOrNull()
            ?.spec as? GoalSpec.WeeklyFrequency
        return (goal?.sessionsPerWeek ?: F.DEFAULT_WEEKLY_TARGET).coerceIn(F.MIN_WEEKLY_TARGET, F.MAX_WEEKLY_TARGET)
    }

    /** Las repeticiones cuentan para ejercicios con peso o de peso corporal (no tiempo ni distancia). */
    private fun countsReps(exerciseId: ExerciseId, exercises: Map<ExerciseId, ExerciseDefinition>): Boolean =
        when (exercises[exerciseId]?.loadType ?: LoadType.WEIGHTED) {
            LoadType.WEIGHTED, LoadType.BODYWEIGHT -> true
            LoadType.TIMED, LoadType.DISTANCE -> false
        }

    /** Volumen de la sesión: total de repeticiones de las series de trabajo. */
    fun volumeReps(session: WorkoutSession, exercises: Map<ExerciseId, ExerciseDefinition>): Int =
        repsByExercise(session, exercises).values.sum()

    /** Repeticiones de trabajo por ejercicio en la sesión. */
    internal fun repsByExercise(session: WorkoutSession, exercises: Map<ExerciseId, ExerciseDefinition>): Map<ExerciseId, Int> {
        val result = LinkedHashMap<ExerciseId, Int>()
        for (entry in session.entries) {
            if (!countsReps(entry.exerciseId, exercises)) continue
            val reps = entry.sets.filter { it.isWorkingSet }.sumOf { it.reps }
            if (reps > 0) result.merge(entry.exerciseId, reps, Int::plus)
        }
        return result
    }

    fun achieves(session: WorkoutSession, spec: GoalSpec.Strength): Boolean =
        session.entries.any { entry ->
            entry.exerciseId == spec.exerciseId && entry.sets.any {
                it.isWorkingSet && it.weightGrams >= spec.targetWeightGrams && it.reps >= spec.minReps
            }
        }

    companion object {
        fun levelFor(totalPoints: Int): Int = floor(sqrt(totalPoints.toDouble() / F.LEVEL_DIVISOR)).toInt() + 1

        /** Puntos acumulados necesarios para alcanzar [level] (inverso de [levelFor]). */
        fun pointsForLevel(level: Int): Int = F.LEVEL_DIVISOR * (level - 1) * (level - 1)
    }
}
