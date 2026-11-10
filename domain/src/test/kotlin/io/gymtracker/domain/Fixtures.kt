package io.gymtracker.domain

import io.gymtracker.domain.event.SignatureVerifier
import io.gymtracker.domain.event.Signer
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.domain.model.Equipment
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseEntry
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.MuscleGroup
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.model.WorkoutSet
import java.time.LocalDate

/** Firma falsa y determinista para pruebas: firma = sha-like simple del mensaje + id. */
class FakeSigner(seed: Char = 'a') : Signer, SignatureVerifier {
    override val authorId = AuthorId(seed.toString().repeat(64))
    override fun sign(message: ByteArray): ByteArray = fakeSig(authorId, message)
    override fun verify(authorId: AuthorId, message: ByteArray, signature: ByteArray): Boolean =
        fakeSig(authorId, message).contentEquals(signature)

    private fun fakeSig(author: AuthorId, message: ByteArray): ByteArray {
        var h = author.hex.hashCode().toLong()
        for (b in message) h = h * 31 + b
        return ByteArray(8) { i -> (h shr (8 * i)).toByte() }
    }
}

val SQUAT = ExerciseId("squat")
val BENCH = ExerciseId("bench")
val PLANK = ExerciseId("plank")

val EXERCISES = mapOf(
    SQUAT to ExerciseDefinition(SQUAT, "Sentadilla", MuscleGroup.QUADS, Equipment.BARBELL, LoadType.WEIGHTED),
    BENCH to ExerciseDefinition(BENCH, "Press banca", MuscleGroup.CHEST, Equipment.BARBELL, LoadType.WEIGHTED),
    PLANK to ExerciseDefinition(PLANK, "Plancha", MuscleGroup.CORE, Equipment.BODYWEIGHT, LoadType.TIMED),
)

fun set(kg: Int, reps: Int = 5, warmup: Boolean = false) = WorkoutSet(reps = reps, weightGrams = kg * 1000, isWarmup = warmup)

fun session(
    date: String,
    sets: List<WorkoutSet> = List(3) { set(100) },
    exercise: ExerciseId = SQUAT,
    id: String = "s-$date-${exercise.value}-${sets.hashCode()}",
    startedAt: Long = LocalDate.parse(date).toEpochDay() * 86_400_000L,
) = WorkoutSession(
    id = SessionId(id),
    localDate = date,
    startedAtEpochMs = startedAt,
    durationSeconds = 3600,
    entries = listOf(ExerciseEntry(exercise, sets)),
)
