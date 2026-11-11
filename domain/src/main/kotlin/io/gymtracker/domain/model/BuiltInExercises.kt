package io.gymtracker.domain.model

import io.gymtracker.domain.model.Equipment.BARBELL
import io.gymtracker.domain.model.Equipment.BODYWEIGHT
import io.gymtracker.domain.model.Equipment.CABLE
import io.gymtracker.domain.model.Equipment.DUMBBELL
import io.gymtracker.domain.model.Equipment.KETTLEBELL
import io.gymtracker.domain.model.Equipment.MACHINE
import io.gymtracker.domain.model.Equipment.OTHER
import io.gymtracker.domain.model.LoadType.DISTANCE
import io.gymtracker.domain.model.LoadType.TIMED
import io.gymtracker.domain.model.LoadType.WEIGHTED
import io.gymtracker.domain.model.MuscleGroup.BACK
import io.gymtracker.domain.model.MuscleGroup.BICEPS
import io.gymtracker.domain.model.MuscleGroup.CALVES
import io.gymtracker.domain.model.MuscleGroup.CARDIO
import io.gymtracker.domain.model.MuscleGroup.CHEST
import io.gymtracker.domain.model.MuscleGroup.CORE
import io.gymtracker.domain.model.MuscleGroup.FULL_BODY
import io.gymtracker.domain.model.MuscleGroup.GLUTES
import io.gymtracker.domain.model.MuscleGroup.HAMSTRINGS
import io.gymtracker.domain.model.MuscleGroup.QUADS
import io.gymtracker.domain.model.MuscleGroup.SHOULDERS
import io.gymtracker.domain.model.MuscleGroup.TRICEPS

/**
 * Catálogo precargado. Los ids son estables ("builtin:...") y nunca se registran como eventos:
 * cualquier teléfono los conoce. Los ejercicios personalizados sí viajan como `exercise_defined`.
 */
object BuiltInExercises {
    private fun ex(id: String, name: String, muscle: MuscleGroup, equipment: Equipment, load: LoadType = WEIGHTED) =
        ExerciseDefinition(ExerciseId("builtin:$id"), name, muscle, equipment, load)

    val all: List<ExerciseDefinition> = listOf(
        // Pecho
        ex("bench_press", "Press de banca", CHEST, BARBELL),
        ex("incline_bench_press", "Press inclinado con barra", CHEST, BARBELL),
        ex("dumbbell_bench_press", "Press de banca con mancuernas", CHEST, DUMBBELL),
        ex("incline_dumbbell_press", "Press inclinado con mancuernas", CHEST, DUMBBELL),
        ex("chest_fly_machine", "Aperturas en máquina", CHEST, MACHINE),
        ex("cable_crossover", "Cruce de poleas", CHEST, CABLE),
        ex("push_up", "Flexiones", CHEST, BODYWEIGHT, LoadType.BODYWEIGHT),
        ex("dips", "Fondos en paralelas", CHEST, BODYWEIGHT, LoadType.BODYWEIGHT),
        // Espalda
        ex("deadlift", "Peso muerto", BACK, BARBELL),
        ex("barbell_row", "Remo con barra", BACK, BARBELL),
        ex("dumbbell_row", "Remo con mancuerna", BACK, DUMBBELL),
        ex("lat_pulldown", "Jalón al pecho", BACK, CABLE),
        ex("seated_cable_row", "Remo sentado en polea", BACK, CABLE),
        ex("pull_up", "Dominadas", BACK, BODYWEIGHT, LoadType.BODYWEIGHT),
        ex("chin_up", "Dominadas supinas", BACK, BODYWEIGHT, LoadType.BODYWEIGHT),
        ex("t_bar_row", "Remo en T", BACK, MACHINE),
        // Hombros
        ex("overhead_press", "Press militar", SHOULDERS, BARBELL),
        ex("dumbbell_shoulder_press", "Press de hombros con mancuernas", SHOULDERS, DUMBBELL),
        ex("lateral_raise", "Elevaciones laterales", SHOULDERS, DUMBBELL),
        ex("front_raise", "Elevaciones frontales", SHOULDERS, DUMBBELL),
        ex("face_pull", "Face pull", SHOULDERS, CABLE),
        ex("rear_delt_fly", "Pájaros", SHOULDERS, DUMBBELL),
        // Brazos
        ex("barbell_curl", "Curl con barra", BICEPS, BARBELL),
        ex("dumbbell_curl", "Curl con mancuernas", BICEPS, DUMBBELL),
        ex("hammer_curl", "Curl martillo", BICEPS, DUMBBELL),
        ex("cable_curl", "Curl en polea", BICEPS, CABLE),
        ex("triceps_pushdown", "Extensión de tríceps en polea", TRICEPS, CABLE),
        ex("skull_crusher", "Press francés", TRICEPS, BARBELL),
        ex("overhead_triceps_extension", "Extensión de tríceps sobre la cabeza", TRICEPS, DUMBBELL),
        ex("close_grip_bench_press", "Press cerrado", TRICEPS, BARBELL),
        // Pierna
        ex("back_squat", "Sentadilla con barra", QUADS, BARBELL),
        ex("front_squat", "Sentadilla frontal", QUADS, BARBELL),
        ex("leg_press", "Prensa de pierna", QUADS, MACHINE),
        ex("leg_extension", "Extensión de cuádriceps", QUADS, MACHINE),
        ex("lunge", "Zancadas", QUADS, DUMBBELL),
        ex("bulgarian_split_squat", "Sentadilla búlgara", QUADS, DUMBBELL),
        ex("goblet_squat", "Sentadilla goblet", QUADS, KETTLEBELL),
        ex("romanian_deadlift", "Peso muerto rumano", HAMSTRINGS, BARBELL),
        ex("leg_curl", "Curl femoral", HAMSTRINGS, MACHINE),
        ex("hip_thrust", "Hip thrust", GLUTES, BARBELL),
        ex("glute_bridge", "Puente de glúteo", GLUTES, BODYWEIGHT, LoadType.BODYWEIGHT),
        ex("calf_raise", "Elevación de talones", CALVES, MACHINE),
        // Core
        ex("plank", "Plancha", CORE, BODYWEIGHT, TIMED),
        ex("crunch", "Abdominales", CORE, BODYWEIGHT, LoadType.BODYWEIGHT),
        ex("hanging_leg_raise", "Elevación de piernas colgado", CORE, BODYWEIGHT, LoadType.BODYWEIGHT),
        ex("cable_crunch", "Crunch en polea", CORE, CABLE),
        ex("ab_wheel", "Rueda abdominal", CORE, OTHER, LoadType.BODYWEIGHT),
        // Cuerpo completo y cardio
        ex("clean_and_press", "Cargada y press", FULL_BODY, BARBELL),
        ex("kettlebell_swing", "Swing con kettlebell", FULL_BODY, KETTLEBELL),
        ex("burpee", "Burpees", FULL_BODY, BODYWEIGHT, LoadType.BODYWEIGHT),
        ex("treadmill", "Cinta de correr", CARDIO, MACHINE, DISTANCE),
        ex("stationary_bike", "Bicicleta estática", CARDIO, MACHINE, TIMED),
        ex("rowing_machine", "Remo ergómetro", CARDIO, MACHINE, DISTANCE),
        ex("jump_rope", "Saltar la cuerda", CARDIO, OTHER, TIMED),
    )

    val byId: Map<ExerciseId, ExerciseDefinition> = all.associateBy { it.id }

    fun isBuiltIn(id: ExerciseId): Boolean = id.value.startsWith("builtin:")
}
