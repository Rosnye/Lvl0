package io.gymtracker.domain.model

import kotlinx.serialization.Serializable

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, FULL_BODY, CARDIO
}

enum class Equipment { BARBELL, DUMBBELL, MACHINE, CABLE, BODYWEIGHT, KETTLEBELL, BAND, OTHER }

/** Cómo se mide el esfuerzo del ejercicio; determina qué cuenta como volumen y como récord. */
enum class LoadType {
    /** Peso externo: el volumen es reps × kg y hay récord de 1RM estimado. */
    WEIGHTED,
    /** Peso corporal, con lastre opcional. Solo el lastre cuenta como volumen. */
    BODYWEIGHT,
    /** Por tiempo (plancha, remo en ergómetro). Sin volumen ni récord en la fórmula v1. */
    TIMED,
    /** Por distancia (correr, caminar). Sin volumen ni récord en la fórmula v1. */
    DISTANCE,
}

@Serializable
data class ExerciseDefinition(
    val id: ExerciseId,
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val loadType: LoadType,
)
