package io.gymtracker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.gymtracker.app.ui.history.HistoryContent
import io.gymtracker.app.ui.history.HistoryUiState
import io.gymtracker.app.ui.history.SessionDetailContent
import io.gymtracker.app.ui.history.SessionItem
import io.gymtracker.app.ui.history.WeekGroup
import io.gymtracker.app.ui.picker.PickerContent
import io.gymtracker.app.ui.picker.PickerUiState
import io.gymtracker.app.ui.theme.GymTrackerTheme
import io.gymtracker.app.ui.workout.TimerUi
import io.gymtracker.app.ui.workout.WorkoutContent
import io.gymtracker.app.ui.workout.WorkoutUiState
import io.gymtracker.domain.model.BuiltInExercises
import io.gymtracker.domain.model.DraftEntry
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.ExerciseEntry
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.MuscleGroup
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutDraft
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.model.WorkoutSet
import io.gymtracker.domain.projection.TrainingState
import io.gymtracker.domain.scoring.ScoringEngine
import java.time.LocalDate

private const val PHONE = "spec:width=411dp,height=891dp,dpi=420"
private val squat = ExerciseId("builtin:back_squat")
private val bench = ExerciseId("builtin:bench_press")

private val draft = WorkoutDraft(
    id = SessionId("draft"),
    startedAtEpochMs = 0,
    entries = listOf(
        DraftEntry(squat, listOf(DraftSet(reps = 10, done = true), DraftSet(reps = 10, done = true), DraftSet(reps = 10), DraftSet(reps = 10))),
        DraftEntry(bench, List(3) { DraftSet(reps = 8) }),
    ),
)

private fun session(date: String, kg: Int, id: String = date) = WorkoutSession(
    id = SessionId(id), localDate = date, startedAtEpochMs = 0, durationSeconds = 3_300,
    entries = listOf(
        ExerciseEntry(squat, List(3) { WorkoutSet(reps = 5) }),
        ExerciseEntry(bench, List(3) { WorkoutSet(reps = 8) }),
    ),
)

private val sessions = listOf(
    session("2026-10-26", 95), session("2026-10-28", 97), session("2026-10-30", 100),
    session("2026-11-02", 100), session("2026-11-04", 102), session("2026-11-06", 100),
    session("2026-11-09", 105),
)
private val training = TrainingState(sessions = sessions)
private val report = ScoringEngine().compute(training, LocalDate.parse("2026-11-10"))
private val items = sessions.asReversed().map { s ->
    SessionItem(s, report.sessionScores.firstOrNull { it.sessionId == s.id }, ScoringEngine().volumeReps(s, training.exercises))
}
private val historyState = HistoryUiState(
    weeks = items.groupBy { io.gymtracker.domain.model.Weeks.startOf(it.session.date) }.map { WeekGroup(it.key, it.value) }.sortedByDescending { it.weekStart },
    exercises = training.exercises,
)

@Composable
private fun Shell(dest: Destination, darkTheme: Boolean = true, content: @Composable () -> Unit) {
    GymTrackerTheme(darkTheme = darkTheme) {
        AppScaffold(selected = dest, onSelect = {}) { padding -> Box(Modifier.padding(padding)) { content() } }
    }
}

@PreviewTest
@Preview(name = "Entrenamiento con descanso", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun WorkoutTimerPreview() = Shell(Destination.TRAIN) {
    WorkoutContent(WorkoutUiState(draft = draft, exercises = BuiltInExercises.byId, elapsedSeconds = 1_505, timer = TimerUi(remainingSeconds = 64, totalSeconds = 90)))
}

@PreviewTest
@Preview(name = "Entrenamiento claro", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun WorkoutLightPreview() = Shell(Destination.TRAIN, darkTheme = false) {
    WorkoutContent(WorkoutUiState(draft = draft, exercises = BuiltInExercises.byId, elapsedSeconds = 1_505))
}

@PreviewTest
@Preview(name = "Selector de ejercicios", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun PickerPreview() = Shell(Destination.TRAIN) {
    PickerContent(PickerUiState(muscle = MuscleGroup.BACK, results = BuiltInExercises.all.filter { it.primaryMuscle == MuscleGroup.BACK }))
}

@PreviewTest
@Preview(name = "Historial", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun HistoryPreview() = Shell(Destination.PROGRESS) { HistoryContent(historyState) }

@PreviewTest
@Preview(name = "Detalle de sesion", device = PHONE, showSystemUi = true, locale = "es")
@Composable
fun SessionDetailPreview() = Shell(Destination.PROGRESS) { SessionDetailContent(items.first(), training.exercises) }
