package io.gymtracker.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Una serie. Los pesos se guardan en gramos (enteros) para que la serialización
 * sea exacta y determinista en todos los dispositivos.
 */
@Serializable
data class WorkoutSet(
    val reps: Int = 0,
    val weightGrams: Int = 0,
    val durationSeconds: Int = 0,
    val distanceMeters: Int = 0,
    /** Esfuerzo percibido 1..10, opcional. */
    val rpe: Int? = null,
    val isWarmup: Boolean = false,
) {
    /** Una serie cuenta si tiene trabajo registrado y no es calentamiento. */
    val isWorkingSet: Boolean
        get() = !isWarmup && (reps > 0 || durationSeconds > 0 || distanceMeters > 0)
}

@Serializable
data class ExerciseEntry(
    val exerciseId: ExerciseId,
    val sets: List<WorkoutSet>,
    val notes: String? = null,
)

@Serializable
data class WorkoutSession(
    val id: SessionId,
    /** Fecha local del autor en formato ISO (yyyy-MM-dd). Define el día y la semana del puntaje. */
    val localDate: String,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val entries: List<ExerciseEntry>,
    val routineName: String? = null,
    val notes: String? = null,
) {
    val date: LocalDate get() = LocalDate.parse(localDate)

    val workingSetCount: Int get() = entries.sumOf { e -> e.sets.count { it.isWorkingSet } }
}
