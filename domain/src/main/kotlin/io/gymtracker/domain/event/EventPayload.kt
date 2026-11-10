package io.gymtracker.domain.event

import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.Goal
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.model.Profile
import io.gymtracker.domain.model.ScoreSummary
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Versión del esquema de eventos. Se incrementa solo con cambios incompatibles. */
const val EVENT_SCHEMA_VERSION: Int = 1

enum class Visibility {
    /** Nunca sale del dispositivo del autor. */
    PRIVATE,
    /** Se envía cifrado a los amigos vinculados. */
    FRIENDS,
}

/**
 * Carga útil de un evento. El registro es solo-anexar: nada se modifica, los cambios
 * se expresan como nuevos eventos (p. ej. SessionDeleted en lugar de borrar).
 */
@Serializable
sealed interface EventPayload {
    /** Nombre estable del tipo (coincide con el discriminador JSON "type"). */
    val kind: String

    /** Visibilidad obligatoria del tipo. Un receptor rechaza eventos que no la respeten. */
    val requiredVisibility: Visibility

    @Serializable
    @SerialName("profile_updated")
    data class ProfileUpdated(val profile: Profile) : EventPayload {
        override val kind get() = "profile_updated"
        override val requiredVisibility get() = Visibility.FRIENDS
    }

    @Serializable
    @SerialName("exercise_defined")
    data class ExerciseDefined(val exercise: ExerciseDefinition) : EventPayload {
        override val kind get() = "exercise_defined"
        override val requiredVisibility get() = Visibility.PRIVATE
    }

    @Serializable
    @SerialName("session_logged")
    data class SessionLogged(val session: WorkoutSession) : EventPayload {
        override val kind get() = "session_logged"
        override val requiredVisibility get() = Visibility.PRIVATE
    }

    @Serializable
    @SerialName("session_deleted")
    data class SessionDeleted(val sessionId: SessionId) : EventPayload {
        override val kind get() = "session_deleted"
        override val requiredVisibility get() = Visibility.PRIVATE
    }

    @Serializable
    @SerialName("goal_created")
    data class GoalCreated(val goal: Goal) : EventPayload {
        override val kind get() = "goal_created"
        override val requiredVisibility get() = Visibility.PRIVATE
    }

    @Serializable
    @SerialName("goal_archived")
    data class GoalArchived(val goalId: GoalId) : EventPayload {
        override val kind get() = "goal_archived"
        override val requiredVisibility get() = Visibility.PRIVATE
    }

    /** Resumen de puntaje derivado del registro privado; es lo que ven los amigos. */
    @Serializable
    @SerialName("score_published")
    data class ScorePublished(val summary: ScoreSummary) : EventPayload {
        override val kind get() = "score_published"
        override val requiredVisibility get() = Visibility.FRIENDS
    }
}
