package io.gymtracker.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Una serie en edición. Solo las marcadas como hechas pasan a la sesión final. */
@Serializable
data class DraftSet(
    val reps: Int = 0,
    val weightGrams: Int = 0,
    val durationSeconds: Int = 0,
    val distanceMeters: Int = 0,
    val isWarmup: Boolean = false,
    val done: Boolean = false,
) {
    fun toWorkoutSet() = WorkoutSet(
        reps = reps,
        weightGrams = weightGrams,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        isWarmup = isWarmup,
    )
}

@Serializable
data class DraftEntry(
    val exerciseId: ExerciseId,
    val sets: List<DraftSet> = listOf(DraftSet()),
)

/**
 * Entrenamiento en curso. Se persiste localmente para sobrevivir al cierre de la app
 * y se convierte en `session_logged` al terminar. Nunca sale del dispositivo.
 */
@Serializable
data class WorkoutDraft(
    val id: SessionId,
    val startedAtEpochMs: Long,
    val entries: List<DraftEntry> = emptyList(),
    val routineName: String? = null,
) {
    val doneSetCount: Int get() = entries.sumOf { e -> e.sets.count { it.done } }

    fun toSession(finishedAtEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): WorkoutSession {
        val localDate = Instant.ofEpochMilli(startedAtEpochMs).atZone(zone).toLocalDate()
        return WorkoutSession(
            id = id,
            localDate = localDate.toString(),
            startedAtEpochMs = startedAtEpochMs,
            durationSeconds = ((finishedAtEpochMs - startedAtEpochMs) / 1000).toInt().coerceAtLeast(0),
            entries = entries
                .map { e -> ExerciseEntry(e.exerciseId, e.sets.filter { it.done }.map { it.toWorkoutSet() }) }
                .filter { it.sets.isNotEmpty() },
            routineName = routineName,
        )
    }

    companion object {
        fun localDateOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
            Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    }
}
