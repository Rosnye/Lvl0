package io.gymtracker.domain.model

import kotlinx.serialization.Serializable

/** Ejercicio de una rutina con sus series de plantilla (sin marcar como hechas). */
@Serializable
data class RoutineExercise(
    val exerciseId: ExerciseId,
    val sets: List<DraftSet>,
)

/** Plantilla de entrenamiento reutilizable. Privada: nunca sale del dispositivo. */
@Serializable
data class Routine(
    val id: RoutineId,
    val name: String,
    val exercises: List<RoutineExercise>,
) {
    val setCount: Int get() = exercises.sumOf { it.sets.size }

    fun toDraft(sessionId: SessionId, startedAtEpochMs: Long): WorkoutDraft = WorkoutDraft(
        id = sessionId,
        startedAtEpochMs = startedAtEpochMs,
        entries = exercises.map { e -> DraftEntry(e.exerciseId, e.sets.map { it.copy(done = false) }.ifEmpty { listOf(DraftSet()) }) },
        routineName = name,
    )

    companion object {
        fun fromSession(id: RoutineId, name: String, session: WorkoutSession) = Routine(
            id = id,
            name = name,
            exercises = session.entries.map { e ->
                RoutineExercise(e.exerciseId, e.sets.map { DraftSet(it.reps, it.weightGrams, it.durationSeconds, it.distanceMeters, it.isWarmup) })
            },
        )
    }
}
