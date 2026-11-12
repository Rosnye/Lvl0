package io.gymtracker.domain

import io.gymtracker.domain.achievements.AchievementEngine
import io.gymtracker.domain.achievements.AchievementId
import io.gymtracker.domain.event.EventFactory
import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.Visibility
import io.gymtracker.domain.goals.GoalTracker
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.model.GoalSpec
import io.gymtracker.domain.model.Routine
import io.gymtracker.domain.model.RoutineExercise
import io.gymtracker.domain.model.RoutineId
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.model.WorkoutSet
import io.gymtracker.domain.projection.Projector
import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoringEngine
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase2Test {
    private val engine = ScoringEngine()
    private val factory = EventFactory(FakeSigner()) { 0 }
    private val today = LocalDate.parse("2026-11-12") // jueves

    private fun state(sessions: List<WorkoutSession>, goals: List<Goal> = emptyList()) =
        TrainingState(exercises = EXERCISES, sessions = sessions, goals = goals)

    // --- Rutinas ---

    @Test
    fun `las rutinas son privadas, se reemplazan por id y el borrado es definitivo`() {
        val r1 = Routine(RoutineId("r1"), "Pierna", listOf(RoutineExercise(SQUAT, listOf(DraftSet(reps = 5, weightGrams = 100_000)))))
        val r2 = Routine(RoutineId("r2"), "Empuje", emptyList())
        val saved = factory.create(1, EventPayload.RoutineSaved(r1))
        assertEquals(Visibility.PRIVATE, saved.body.visibility)
        val events = listOf(
            saved,
            factory.create(2, EventPayload.RoutineSaved(r2)),
            factory.create(3, EventPayload.RoutineSaved(r1.copy(name = "Pierna pesada"))),
            factory.create(4, EventPayload.RoutineDeleted(RoutineId("r2"))),
            factory.create(5, EventPayload.RoutineSaved(r2)), // tras borrar no reaparece
        )
        val routines = Projector.project(events).routines
        assertEquals(listOf("Pierna pesada"), routines.map { it.name })
    }

    @Test
    fun `una rutina se convierte en borrador sin series hechas y se crea desde una sesion`() {
        val session = session("2026-11-09", sets = listOf(set(60, warmup = true), set(100), set(100)))
        val routine = Routine.fromSession(RoutineId("r"), "Lunes", session)
        assertEquals(3, routine.setCount)
        val draft = routine.toDraft(SessionId("s"), 1_000)
        assertEquals("Lunes", draft.routineName)
        assertTrue(draft.entries.single().sets.none { it.done })
        assertTrue(draft.entries.single().sets.first().isWarmup)
        assertEquals(100_000, draft.entries.single().sets.last().weightGrams)
    }

    // --- Metas ---

    @Test
    fun `entre metas de frecuencia del mismo dia manda la ultima creada`() {
        val goals = listOf(
            Goal(GoalId("a"), GoalSpec.WeeklyFrequency(5), "2026-11-10"),
            Goal(GoalId("b"), GoalSpec.WeeklyFrequency(2), "2026-11-10"),
        )
        assertEquals(2, engine.weeklyTargetFor(today, goals))
        assertEquals(3, engine.weeklyTargetFor(LocalDate.parse("2026-11-09"), goals)) // antes: valor por defecto
    }

    @Test
    fun `cambiar la meta de frecuencia no altera las semanas anteriores`() {
        val sessions = listOf(session("2026-11-02"), session("2026-11-04"), session("2026-11-06"))
        val before = engine.compute(state(sessions), today).totalPoints
        val changed = engine.compute(state(sessions, listOf(Goal(GoalId("g"), GoalSpec.WeeklyFrequency(5), "2026-11-10"))), today).totalPoints
        assertEquals(before, changed)
    }

    @Test
    fun `progreso de la meta semanal y de una meta de fuerza`() {
        val goals = listOf(
            Goal(GoalId("f"), GoalSpec.WeeklyFrequency(4), "2026-11-01"),
            Goal(GoalId("s"), GoalSpec.Strength(SQUAT, targetWeightGrams = 120_000, minReps = 5), "2026-11-01"),
            Goal(GoalId("b"), GoalSpec.Strength(BENCH, targetWeightGrams = 80_000, minReps = 1), "2026-11-01"),
        )
        val sessions = listOf(
            session("2026-11-09", sets = List(3) { set(110) }),
            session("2026-11-11", sets = listOf(set(115), set(125, reps = 3), set(100))),
            session("2026-11-11", sets = List(3) { set(80, reps = 2) }, exercise = BENCH, id = "bench", startedAt = 5),
        )
        val s = state(sessions, goals)
        val overview = GoalTracker.overview(s, engine.compute(s, today), today)

        assertEquals(4, overview.frequency.target)
        assertEquals(3, overview.frequency.thisWeek)
        assertFalse(overview.frequency.reached)
        assertEquals("f", overview.frequency.goal?.id?.value)

        val squat = overview.activeStrength.single()
        assertEquals(115_000, squat.bestWeightGrams) // 125 kg fue a 3 reps, no cuenta para minReps = 5
        assertEquals(115f / 120f, squat.fraction)
        assertNull(squat.achievedOn)

        val bench = overview.achievedStrength.single()
        assertEquals(LocalDate.parse("2026-11-11"), bench.achievedOn)
        assertEquals(1f, bench.fraction)
    }

    @Test
    fun `una meta de fuerza con peso cero mide repeticiones`() {
        val goal = Goal(GoalId("p"), GoalSpec.Strength(SQUAT, targetWeightGrams = 0, minReps = 15), "2026-11-01")
        val s = state(listOf(session("2026-11-09", sets = List(3) { set(0, reps = 12) })), listOf(goal))
        val progress = GoalTracker.overview(s, engine.compute(s, today), today).activeStrength.single()
        assertTrue(progress.isRepsGoal)
        assertEquals(12, progress.bestReps)
        assertEquals(12f / 15f, progress.fraction)
    }

    // --- Insignias ---

    @Test
    fun `sin sesiones no hay insignias y el nivel parte en 1`() {
        val s = state(emptyList())
        val all = AchievementEngine.evaluate(s, engine.compute(s, today))
        assertEquals(AchievementId.entries.size, all.size)
        assertTrue(all.none { it.unlocked })
        assertEquals(1, all.first { it.id == AchievementId.LEVEL_5 }.progress)
    }

    @Test
    fun `insignias de primera sesion, semana cumplida, racha y record con su fecha`() {
        // 4 semanas seguidas de 3 sesiones (meta por defecto) y un récord en la última.
        val weeks = listOf("2026-10-19", "2026-10-26", "2026-11-02", "2026-11-09")
        val sessions = weeks.flatMapIndexed { w, monday ->
            val d = LocalDate.parse(monday)
            listOf(0L, 2L, 4L).map { offset -> session(d.plusDays(offset).toString(), sets = List(3) { set(100 + w * 5) }) }
        }
        val s = state(sessions)
        val byId = AchievementEngine.evaluate(s, engine.compute(s, today)).associateBy { it.id }

        assertEquals(LocalDate.parse("2026-10-19"), byId.getValue(AchievementId.FIRST_SESSION).unlockedOn)
        assertEquals(LocalDate.parse("2026-10-23"), byId.getValue(AchievementId.WEEKLY_GOAL).unlockedOn)
        assertEquals(LocalDate.parse("2026-11-13"), byId.getValue(AchievementId.STREAK_4).unlockedOn)
        assertEquals(LocalDate.parse("2026-11-09"), byId.getValue(AchievementId.SESSIONS_10).unlockedOn) // 10ª sesión
        assertNotNull(byId.getValue(AchievementId.FIRST_PR).unlockedOn)
        assertEquals(12, byId.getValue(AchievementId.SESSIONS_50).progress)
        assertNull(byId.getValue(AchievementId.STREAK_12).unlockedOn)
        assertEquals(4, byId.getValue(AchievementId.STREAK_12).progress)
    }

    @Test
    fun `insignia de sesion larga y de volumen acumulado`() {
        val long = WorkoutSession(
            id = SessionId("long"), localDate = "2026-11-09", startedAtEpochMs = 0, durationSeconds = 95 * 60,
            entries = listOf(io.gymtracker.domain.model.ExerciseEntry(SQUAT, List(10) { WorkoutSet(reps = 10, weightGrams = 100_000) })),
        )
        val s = state(listOf(long))
        val byId = AchievementEngine.evaluate(s, engine.compute(s, today)).associateBy { it.id }
        assertTrue(byId.getValue(AchievementId.LONG_SESSION).unlocked)
        assertTrue(byId.getValue(AchievementId.VOLUME_10T).unlocked) // 10 × 10 × 100 kg = 10 000 kg
        assertFalse(byId.getValue(AchievementId.VOLUME_100T).unlocked)
    }

    @Test
    fun `puntos necesarios por nivel son el inverso de levelFor`() {
        for (level in 1..12) {
            val points = ScoringEngine.pointsForLevel(level)
            assertEquals(level, ScoringEngine.levelFor(points))
            if (points > 0) assertEquals(level - 1, ScoringEngine.levelFor(points - 1))
        }
    }
}
