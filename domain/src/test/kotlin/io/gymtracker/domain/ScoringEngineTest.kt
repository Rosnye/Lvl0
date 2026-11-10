package io.gymtracker.domain

import io.gymtracker.domain.model.ExerciseEntry
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.model.GoalSpec
import io.gymtracker.domain.model.WorkoutSet
import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoringEngine
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoringEngineTest {
    private val engine = ScoringEngine()
    private val today = LocalDate.parse("2026-11-10") // martes

    private fun score(vararg sessions: io.gymtracker.domain.model.WorkoutSession, goals: List<Goal> = emptyList()) =
        engine.compute(TrainingState(exercises = EXERCISES, sessions = sessions.toList(), goals = goals), today)

    @Test
    fun `sin sesiones no hay puntos y el nivel es 1`() {
        val r = score()
        assertEquals(0, r.totalPoints)
        assertEquals(1, r.level)
        assertEquals(0, r.currentStreakWeeks)
    }

    @Test
    fun `una sesion valida da los 100 puntos base`() {
        val r = score(session("2026-11-09"))
        assertEquals(100, r.totalPoints)
        assertEquals(1, r.sessionScores.size)
    }

    @Test
    fun `una sesion con menos de 3 series de trabajo no puntua`() {
        val r = score(session("2026-11-09", sets = listOf(set(100), set(100), set(100, warmup = true))))
        assertEquals(0, r.totalPoints)
    }

    @Test
    fun `solo la primera sesion del dia suma base`() {
        val r = score(session("2026-11-09", id = "a"), session("2026-11-09", id = "b", startedAt = 1))
        assertEquals(100, r.totalPoints)
    }

    @Test
    fun `una sesion de cardio o por tiempo tambien da base pero sin volumen ni records`() {
        val sets = List(3) { WorkoutSet(durationSeconds = 60) }
        val r = score(session("2026-11-09", sets = sets, exercise = PLANK))
        assertEquals(100, r.totalPoints)
        assertEquals(0.0, r.sessionScores.single().volumeKg)
    }

    @Test
    fun `bono de volumen tras 3 sesiones de linea base`() {
        // 3 sesiones de 1500 kg (3x5x100) y una cuarta de 2250 kg (3x5x150): ratio 1.5 -> +25
        val r = score(
            session("2026-10-20"), session("2026-10-22"), session("2026-10-24"),
            session("2026-10-27", sets = List(3) { set(150) }),
        )
        val last = r.sessionScores.last()
        assertEquals(25, last.volumeBonus)
    }

    @Test
    fun `el bono de volumen no es negativo y se limita a 50`() {
        val r = score(
            session("2026-10-20"), session("2026-10-22"), session("2026-10-24"),
            session("2026-10-26", sets = List(3) { set(50) }),              // ratio 0.5 -> 0
            session("2026-10-28", sets = List(3) { set(100, reps = 20) }),  // ratio alto -> 50
        )
        assertEquals(0, r.sessionScores[3].volumeBonus)
        assertEquals(50, r.sessionScores[4].volumeBonus)
    }

    @Test
    fun `un record solo puntua con 3 sesiones previas del ejercicio`() {
        val r = score(
            session("2026-10-20", sets = List(3) { set(100) }),
            session("2026-10-22", sets = List(3) { set(105) }), // mejora, pero sin historial: no puntúa
            session("2026-10-24", sets = List(3) { set(110) }),
            session("2026-10-26", sets = List(3) { set(120) }), // 4ª sesión: récord válido
        )
        assertEquals(0, r.sessionScores[1].prBonus)
        assertEquals(30, r.sessionScores[3].prBonus)
        assertEquals(1, r.sessionScores[3].prCount)
        assertEquals(ScoringEngine.epleyGrams(120_000, 5), r.bestE1rmGrams[SQUAT])
    }

    @Test
    fun `los records se limitan a 2 por sesion`() {
        val three = listOf(SQUAT, BENCH, io.gymtracker.domain.model.ExerciseId("row")).let { ids ->
            fun s(date: String, kg: Int) = session(date).copy(entries = ids.map { ExerciseEntry(it, List(3) { set(kg) }) })
            listOf(s("2026-10-01", 100), s("2026-10-03", 100), s("2026-10-05", 100), s("2026-10-07", 130))
        }
        val r = score(*three.toTypedArray())
        assertEquals(3, r.sessionScores.last().prCount)
        assertEquals(60, r.sessionScores.last().prBonus)
    }

    @Test
    fun `la racha multiplica tras semanas completas con la meta cumplida`() {
        // Semana 1 (lun 2026-10-19) y semana 2 (lun 2026-10-26): 3 sesiones cada una.
        val r = score(
            session("2026-10-19"), session("2026-10-21"), session("2026-10-23"),
            session("2026-10-26"), session("2026-10-28"), session("2026-10-30"),
            session("2026-11-02"),
        )
        val third = r.sessionScores.last()
        assertEquals(2, third.streakWeeks)
        assertEquals(1.2, third.multiplier)
        assertEquals(120, third.points) // 100 × 1.2, sin volumen (igual al promedio) ni récord
        // Hoy es 2026-11-10: la semana anterior (2 nov) solo tuvo 1 sesión, así que la racha vigente se cortó.
        assertEquals(0, r.currentStreakWeeks)
    }

    @Test
    fun `la racha vigente cuenta la semana actual si ya cumplio la meta`() {
        val r = score(
            session("2026-11-02"), session("2026-11-04"), session("2026-11-06"),
            session("2026-11-09"), session("2026-11-10"), session("2026-11-10", id = "x", startedAt = 5),
        )
        // 10-nov tiene 2 sesiones pero cuentan como sesiones para la frecuencia; semana actual = 3 sesiones válidas
        assertEquals(2, r.currentStreakWeeks)
    }

    @Test
    fun `bono de 100 al alcanzar la meta semanal`() {
        val r = score(session("2026-11-02"), session("2026-11-04"), session("2026-11-06"))
        assertEquals(listOf(0, 0, 100), r.sessionScores.map { it.goalBonus })
        assertEquals(400, r.totalPoints)
    }

    @Test
    fun `la meta de frecuencia del usuario reemplaza a la de defecto`() {
        val goal = Goal(GoalId("g"), GoalSpec.WeeklyFrequency(2), createdLocalDate = "2026-11-01")
        val r = score(session("2026-11-02"), session("2026-11-04"), goals = listOf(goal))
        assertEquals(100, r.sessionScores[1].goalBonus)
    }

    @Test
    fun `bono unico de 200 al cumplir una meta de fuerza`() {
        val goal = Goal(GoalId("g"), GoalSpec.Strength(SQUAT, targetWeightGrams = 120_000, minReps = 3), createdLocalDate = "2026-11-01")
        val r = score(
            session("2026-10-30", sets = List(3) { set(120) }), // antes de la meta: no cuenta
            session("2026-11-03", sets = List(3) { set(120) }),
            session("2026-11-05", sets = List(3) { set(125) }),
            goals = listOf(goal),
        )
        assertEquals(listOf(0, 200, 0), r.sessionScores.map { it.goalBonus })
    }

    @Test
    fun `nivel segun puntos acumulados`() {
        assertEquals(1, ScoringEngine.levelFor(0))
        assertEquals(1, ScoringEngine.levelFor(499))
        assertEquals(2, ScoringEngine.levelFor(500))
        assertEquals(3, ScoringEngine.levelFor(2000))
        assertEquals(4, ScoringEngine.levelFor(4500))
    }

    @Test
    fun `el resumen semanal refleja la semana pedida`() {
        val r = score(session("2026-11-09"), session("2026-11-02"))
        val s = r.summaryFor(today)
        assertEquals("2026-11-09", s.weekStart)
        assertEquals(100, s.weekPoints)
        assertEquals(1, s.weekSessions)
        assertEquals(200, s.totalPoints)
    }

    @Test
    fun `el calculo es determinista independientemente del orden de entrada`() {
        val a = listOf(session("2026-10-20"), session("2026-10-22", sets = List(3) { set(110) }), session("2026-10-24"))
        assertEquals(score(*a.toTypedArray()), score(*a.reversed().toTypedArray()))
    }
}
