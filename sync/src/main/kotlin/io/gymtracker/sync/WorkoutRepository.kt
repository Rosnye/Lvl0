package io.gymtracker.sync

import io.gymtracker.data.draft.WorkoutDraftStore
import io.gymtracker.domain.event.EventPayload
import io.gymtracker.domain.model.DraftEntry
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.Equipment
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.MuscleGroup
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutDraft
import io.gymtracker.domain.model.WorkoutSession
import io.gymtracker.domain.projection.TrainingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Casos de uso del entrenamiento propio: borrador en curso, sesiones, ejercicios personalizados. */
@Singleton
class WorkoutRepository @Inject constructor(
    private val localAuthor: LocalAuthor,
    private val draftStore: WorkoutDraftStore,
) {
    val draft: Flow<WorkoutDraft?> = draftStore.draft

    fun observeState(): Flow<TrainingState> = localAuthor.observeState()

    suspend fun startWorkout(now: Long = System.currentTimeMillis()): WorkoutDraft {
        draftStore.draft.first()?.let { return it }
        val draft = WorkoutDraft(id = SessionId(UUID.randomUUID().toString()), startedAtEpochMs = now)
        draftStore.save(draft)
        return draft
    }

    suspend fun updateDraft(transform: (WorkoutDraft) -> WorkoutDraft) {
        val current = draftStore.draft.first() ?: return
        draftStore.save(transform(current))
    }

    suspend fun addExercise(exerciseId: ExerciseId, lastSets: List<DraftSet>? = null) = updateDraft { d ->
        val template = lastSets?.map { it.copy(done = false) }?.takeIf { it.isNotEmpty() } ?: listOf(DraftSet())
        d.copy(entries = d.entries + DraftEntry(exerciseId, template))
    }

    suspend fun discardWorkout() = draftStore.clear()

    /** Convierte el borrador en una sesión registrada y publica el puntaje. Devuelve null si no hay series hechas. */
    suspend fun finishWorkout(now: Long = System.currentTimeMillis()): WorkoutSession? {
        val draft = draftStore.draft.first() ?: return null
        val session = draft.toSession(now)
        if (session.entries.isEmpty()) return null
        localAuthor.recordAndPublish(EventPayload.SessionLogged(session))
        draftStore.clear()
        return session
    }

    suspend fun deleteSession(id: SessionId) {
        localAuthor.recordAndPublish(EventPayload.SessionDeleted(id))
    }

    suspend fun createCustomExercise(name: String, muscle: MuscleGroup, equipment: Equipment, loadType: LoadType): ExerciseDefinition {
        val exercise = ExerciseDefinition(ExerciseId("custom:${UUID.randomUUID()}"), name.trim(), muscle, equipment, loadType)
        localAuthor.record(EventPayload.ExerciseDefined(exercise))
        return exercise
    }

    /** Últimas series hechas de un ejercicio, para prellenar la siguiente vez. */
    suspend fun lastSetsOf(exerciseId: ExerciseId): List<DraftSet>? {
        val state = localAuthor.currentState()
        val entry = state.sessions.asReversed().firstNotNullOfOrNull { s -> s.entries.firstOrNull { it.exerciseId == exerciseId } }
            ?: return null
        return entry.sets.map { DraftSet(it.reps, it.weightGrams, it.durationSeconds, it.distanceMeters, it.isWarmup) }
    }
}
