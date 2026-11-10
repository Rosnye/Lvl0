package io.gymtracker.domain.projection

import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.event.SignedEvent
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSession

/** Estado actual del entrenamiento propio, derivado del registro de eventos. */
data class TrainingState(
    val profile: Profile? = null,
    val exercises: Map<ExerciseId, ExerciseDefinition> = emptyMap(),
    val sessions: List<WorkoutSession> = emptyList(),
    /** Metas activas (las archivadas no aparecen). */
    val goals: List<Goal> = emptyList(),
)

/** Reconstruye el estado aplicando los eventos en orden de seq. */
object Projector {
    fun project(events: List<SignedEvent>): TrainingState {
        var profile: Profile? = null
        val exercises = LinkedHashMap<ExerciseId, ExerciseDefinition>()
        val sessions = LinkedHashMap<SessionId, WorkoutSession>()
        val deletedSessions = HashSet<SessionId>()
        val goals = LinkedHashMap<GoalId, Goal>()
        val archivedGoals = HashSet<GoalId>()

        for (event in events.sortedBy { it.seq }) {
            when (val p = event.payload) {
                is EventPayload.ProfileUpdated -> profile = p.profile
                is EventPayload.ExerciseDefined -> exercises[p.exercise.id] = p.exercise
                is EventPayload.SessionLogged -> if (p.session.id !in deletedSessions) sessions[p.session.id] = p.session
                is EventPayload.SessionDeleted -> { deletedSessions += p.sessionId; sessions.remove(p.sessionId) }
                is EventPayload.GoalCreated -> if (p.goal.id !in archivedGoals) goals[p.goal.id] = p.goal
                is EventPayload.GoalArchived -> { archivedGoals += p.goalId; goals.remove(p.goalId) }
                is EventPayload.ScorePublished -> Unit // derivado, no afecta al estado
            }
        }
        return TrainingState(
            profile = profile,
            exercises = exercises,
            sessions = sessions.values.sortedWith(compareBy({ it.localDate }, { it.startedAtEpochMs }, { it.id.value })),
            goals = goals.values.toList(),
        )
    }
}
