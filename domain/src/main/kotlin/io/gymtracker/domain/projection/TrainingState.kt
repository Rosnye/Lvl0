package io.gymtracker.domain.projection

import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.SignedEvent
import io.gymtracker.domain.model.BuiltInExercises
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.model.Routine
import io.gymtracker.domain.model.RoutineId
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSession

/** Estado actual del entrenamiento propio, derivado del registro de eventos. */
data class TrainingState(
    val profile: Profile? = null,
    /** Catálogo precargado + ejercicios personalizados definidos por eventos. */
    val exercises: Map<ExerciseId, ExerciseDefinition> = BuiltInExercises.byId,
    val sessions: List<WorkoutSession> = emptyList(),
    /** Metas activas (las archivadas no aparecen), en orden de creación. */
    val goals: List<Goal> = emptyList(),
    val routines: List<Routine> = emptyList(),
)

/** Reconstruye el estado aplicando los eventos en orden de seq. */
object Projector {
    fun project(events: List<SignedEvent>, builtIn: Map<ExerciseId, ExerciseDefinition> = BuiltInExercises.byId): TrainingState {
        var profile: Profile? = null
        val exercises = LinkedHashMap<ExerciseId, ExerciseDefinition>(builtIn)
        val sessions = LinkedHashMap<SessionId, WorkoutSession>()
        val deletedSessions = HashSet<SessionId>()
        val goals = LinkedHashMap<GoalId, Goal>()
        val archivedGoals = HashSet<GoalId>()
        val routines = LinkedHashMap<RoutineId, Routine>()
        val deletedRoutines = HashSet<RoutineId>()

        for (event in events.sortedBy { it.seq }) {
            when (val p = event.payload) {
                is EventPayload.ProfileUpdated -> profile = p.profile
                is EventPayload.ExerciseDefined -> exercises[p.exercise.id] = p.exercise
                is EventPayload.SessionLogged -> if (p.session.id !in deletedSessions) sessions[p.session.id] = p.session
                is EventPayload.SessionDeleted -> { deletedSessions += p.sessionId; sessions.remove(p.sessionId) }
                is EventPayload.GoalCreated -> if (p.goal.id !in archivedGoals) goals[p.goal.id] = p.goal
                is EventPayload.GoalArchived -> { archivedGoals += p.goalId; goals.remove(p.goalId) }
                is EventPayload.RoutineSaved -> if (p.routine.id !in deletedRoutines) routines[p.routine.id] = p.routine
                is EventPayload.RoutineDeleted -> { deletedRoutines += p.routineId; routines.remove(p.routineId) }
                is EventPayload.ScorePublished -> Unit // derivado, no afecta al estado
            }
        }
        return TrainingState(
            profile = profile,
            exercises = exercises,
            sessions = sessions.values.sortedWith(compareBy({ it.localDate }, { it.startedAtEpochMs }, { it.id.value })),
            goals = goals.values.toList(),
            routines = routines.values.sortedBy { it.name.lowercase() },
        )
    }
}
