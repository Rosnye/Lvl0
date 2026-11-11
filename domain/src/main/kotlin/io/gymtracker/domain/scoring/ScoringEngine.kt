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
import io.gymtracker.domain.scoring.ScoreFormulaV1 as F
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

        val bestE1rm = HashMap<ExerciseId, Int>()
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

            // 2. Volumen relativo al propio promedio reciente
            val volume = volumeKg(session, state.exercises)
            val windowStart = date.minusDays(F.VOLUME_WINDOW_DAYS)
            val baseline = volumeHistory.filter { (d, _) -> d >= windowStart && d < date }.map { it.second }
            val volumeBonus = if (firstOfDay && baseline.size >= F.VOLUME_MIN_HISTORY && baseline.average() > 0.0) {
                val ratio = volume / baseline.average()
                (F.VOLUME_BONUS_MAX * (ratio - 1.0)).roundToInt().coerceIn(0, F.VOLUME_BONUS_MAX)
            } else 0
            volumeHistory += date to volume

            // 3. Récords personales (1RM estimado, Epley)
            var prCount = 0
            for ((exerciseId, e1rm) in bestE1rmBySession(session, state.exercises)) {
                val prior = priorSessionsPerExercise[exerciseId] ?: 0
                val best = bestE1rm[exerciseId] ?: 0
                if (e1rm > best) {
                    if (prior >= F.PR_MIN_PRIOR_SESSIONS) prCount++
                    bestE1rm[exerciseId] = e1rm
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
            if (countInWeek == target && weeklyBonusAwarded.add(week)) goalBonus += F.WEEKLY_GOAL_BONUS
            for (goal in state.goals) {
                val spec = goal.spec as? GoalSpec.Strength ?: continue
                if (goal.id in strengthGoalsAwarded || date < goal.createdDate) continue
                if (achieves(session, spec)) {
                    strengthGoalsAwarded += goal.id
                    goalBonus += F.STRENGTH_GOAL_BONUS
                }
            }

            val points = ((base + volumeBonus + prBonus) * multiplier).roundToInt() + goalBonus
            scores += SessionScore(
                sessionId = session.id,
                localDate = date,
                basePoints = base,
                volumeKg = volume,
                volumeBonus = volumeBonus,
                prCount = prCount,
                prBonus = prBonus,
                streakWeeks = streak,
                multiplier = multiplier,
                goalBonus = goalBonus,
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
            bestE1rmGrams = bestE1rm,
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

    /** Meta de frecuencia vigente en [date]: la última creada hasta esa fecha, o la de defecto. */
    internal fun weeklyTargetFor(date: LocalDate, goals: List<Goal>): Int {
        val goal = goals
            .filter { it.spec is GoalSpec.WeeklyFrequency && it.createdDate <= date }
            .maxByOrNull { it.createdDate }
            ?.spec as? GoalSpec.WeeklyFrequency
        return (goal?.sessionsPerWeek ?: F.DEFAULT_WEEKLY_TARGET).coerceIn(F.MIN_WEEKLY_TARGET, F.MAX_WEEKLY_TARGET)
    }

    private fun countsLoad(exerciseId: ExerciseId, weightGrams: Int, exercises: Map<ExerciseId, ExerciseDefinition>): Boolean =
        when (exercises[exerciseId]?.loadType ?: LoadType.WEIGHTED) {
            LoadType.WEIGHTED -> true
            LoadType.BODYWEIGHT -> weightGrams > 0
            LoadType.TIMED, LoadType.DISTANCE -> false
        }

    /** Volumen en kg: suma de reps × peso de las series de trabajo con carga. */
    fun volumeKg(session: WorkoutSession, exercises: Map<ExerciseId, ExerciseDefinition>): Double {
        var grams = 0L
        for (entry in session.entries) for (set in entry.sets) {
            if (set.isWorkingSet && set.reps > 0 && countsLoad(entry.exerciseId, set.weightGrams, exercises)) {
                grams += set.reps.toLong() * set.weightGrams
            }
        }
        return grams / 1000.0
    }

    /** Mejor 1RM estimado por ejercicio en la sesión. Epley: peso × (1 + reps / 30). */
    internal fun bestE1rmBySession(session: WorkoutSession, exercises: Map<ExerciseId, ExerciseDefinition>): Map<ExerciseId, Int> {
        val result = HashMap<ExerciseId, Int>()
        for (entry in session.entries) for (set in entry.sets) {
            if (!set.isWorkingSet || set.reps !in 1..F.PR_MAX_REPS) continue
            if (!countsLoad(entry.exerciseId, set.weightGrams, exercises) || set.weightGrams <= 0) continue
            val e1rm = epleyGrams(set.weightGrams, set.reps)
            result.merge(entry.exerciseId, e1rm, ::maxOf)
        }
        return result
    }

    private fun achieves(session: WorkoutSession, spec: GoalSpec.Strength): Boolean =
        session.entries.any { entry ->
            entry.exerciseId == spec.exerciseId && entry.sets.any {
                it.isWorkingSet && it.weightGrams >= spec.targetWeightGrams && it.reps >= spec.minReps
            }
        }

    companion object {
        fun epleyGrams(weightGrams: Int, reps: Int): Int = (weightGrams * (1.0 + reps / 30.0)).roundToInt()

        fun levelFor(totalPoints: Int): Int = floor(sqrt(totalPoints.toDouble() / F.LEVEL_DIVISOR)).toInt() + 1
    }
}
