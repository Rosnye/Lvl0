package io.gymtracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.gymtracker.app.ui.goal.NewGoalState
import io.gymtracker.app.ui.goal.NewStrengthGoalContent
import io.gymtracker.app.ui.home.HomeContent
import io.gymtracker.app.ui.home.HomeUiState
import io.gymtracker.app.ui.home.RecentSession
import io.gymtracker.app.ui.progress.MonthCalendar
import io.gymtracker.app.ui.progress.ProgressContent
import io.gymtracker.app.ui.progress.ProgressUiState
import io.gymtracker.app.ui.routine.RoutineEditorContent
import io.gymtracker.app.ui.routine.RoutineEditorState
import io.gymtracker.app.ui.theme.GymTrackerTheme
import io.gymtracker.domain.achievements.AchievementEngine
import io.gymtracker.domain.goals.GoalTracker
import io.gymtracker.domain.model.BuiltInExercises
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.ExerciseEntry
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.model.GoalSpec
import io.gymtracker.domain.model.Routine
import io.gymtracker.domain.model.RoutineExercise
import io.gymtracker.domain.model.RoutineId
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.model.WorkoutSet
import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoringEngine
import java.time.LocalDate
import java.time.YearMonth

private const val PHONE = "spec:width=411dp,height=891dp,dpi=420"
private const val TALL = "spec:width=411dp,height=2000dp,dpi=420"
private val today = LocalDate.parse("2026-11-12")
private val squat = ExerciseId("builtin:back_squat")
private val bench = ExerciseId("builtin:bench_press")
private val pullUp = ExerciseId("builtin:pull_up")

private fun sample(date: LocalDate, variation: Int, i: Int) = WorkoutSession(
    id = SessionId("s$i"), localDate = date.toString(), startedAtEpochMs = 0, durationSeconds = if (i == 5) 95 * 60 else 3_300,
    entries = listOf(
        ExerciseEntry(squat, List(3) { WorkoutSet(reps = 8 + variation % 4) }),
        ExerciseEntry(bench, List(3) { WorkoutSet(reps = 8) }),
        ExerciseEntry(pullUp, List(3) { WorkoutSet(reps = 9) }),
    ),
)

/** Cuatro semanas completas (lunes, miércoles y viernes) y la actual en curso, con progresión en sentadilla. */
private val sessions = (0 until 12).map { i ->
    val monday = LocalDate.parse("2026-10-12").plusWeeks((i / 3).toLong())
    sample(monday.plusDays(listOf(0L, 2L, 4L)[i % 3]), 90 + i, i)
} + sample(LocalDate.parse("2026-11-09"), 106, 15) + sample(LocalDate.parse("2026-11-11"), 107, 16)

private val goals = listOf(
    Goal(GoalId("f"), GoalSpec.WeeklyFrequency(3), "2026-10-01"),
    Goal(GoalId("sq"), GoalSpec.Strength(squat, 0, 15), "2026-10-01"),
    Goal(GoalId("pu"), GoalSpec.Strength(pullUp, 0, 12), "2026-10-01"),
    Goal(GoalId("bp"), GoalSpec.Strength(bench, 0, 8), "2026-10-01"),
)
private val training = TrainingState(sessions = sessions, goals = goals)
private val report = ScoringEngine().compute(training, today)

private val routines = listOf(
    Routine(RoutineId("a"), "Pierna y empuje", listOf(
        RoutineExercise(squat, List(3) { DraftSet(reps = 5) }),
        RoutineExercise(bench, List(3) { DraftSet(reps = 8) }),
    )),
    Routine(RoutineId("b"), "Tirón", listOf(
        RoutineExercise(pullUp, List(4) { DraftSet(reps = 8) }),
        RoutineExercise(ExerciseId("builtin:barbell_row"), List(3) { DraftSet(reps = 8) }),
    )),
)

private val progressState = ProgressUiState(
    level = report.level,
    totalPoints = report.totalPoints,
    levelStartPoints = ScoringEngine.pointsForLevel(report.level),
    nextLevelPoints = ScoringEngine.pointsForLevel(report.level + 1),
    streakWeeks = report.currentStreakWeeks,
    calendar = MonthCalendar(YearMonth.from(today), sessions.map { it.date }.filter { YearMonth.from(it) == YearMonth.from(today) }.toSet(), today),
    goals = GoalTracker.overview(training, report, today),
    achievements = AchievementEngine.evaluate(training, report),
    exercises = BuiltInExercises.byId,
)

@Composable
private fun Shell(dest: Destination, content: @Composable () -> Unit) {
    GymTrackerTheme(darkTheme = true) {
        AppScaffold(selected = dest, onSelect = {}) { padding -> Box(Modifier.padding(padding)) { content() } }
    }
}

@PreviewTest
@Preview(name = "Progreso", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun ProgressPreview() = Shell(Destination.PROGRESS) { ProgressContent(progressState) }

@PreviewTest
@Preview(name = "Progreso completo", device = TALL, locale = "es")
@Composable
fun ProgressFullPreview() = Shell(Destination.PROGRESS) { ProgressContent(progressState) }

@PreviewTest
@Preview(name = "Inicio con rutinas", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun HomeRoutinesPreview() = Shell(Destination.TRAIN) {
    HomeContent(
        HomeUiState(
            fingerprint = "9f3a-1c77-b2e0-4d58",
            totalPoints = report.totalPoints,
            weekPoints = report.summaryFor(today).weekPoints,
            level = report.level,
            streakWeeks = report.currentStreakWeeks,
            sessionCount = sessions.size,
            weekSessions = report.summaryFor(today).weekSessions,
            weeklyTarget = 3,
            routines = routines,
            recent = sessions.takeLast(2).reversed().map { s -> RecentSession(s.copy(routineName = "Pierna y empuje"), report.sessionScores.firstOrNull { it.sessionId == s.id }) },
        ),
    )
}

@PreviewTest
@Preview(name = "Editor de rutina", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun RoutineEditorPreview() = Shell(Destination.TRAIN) {
    RoutineEditorContent(RoutineEditorState(routine = routines.first(), isNew = false, exercises = BuiltInExercises.byId))
}

@PreviewTest
@Preview(name = "Nueva meta de fuerza", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun NewGoalPreview() = Shell(Destination.PROGRESS) {
    NewStrengthGoalContent(NewGoalState(exercise = BuiltInExercises.byId[pullUp], repsText = "15", currentBest = "9 reps"))
}
