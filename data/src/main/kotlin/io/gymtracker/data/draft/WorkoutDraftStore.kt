package io.gymtracker.data.draft

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.gymtracker.domain.event.EventCodec
import io.gymtracker.domain.model.WorkoutDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.draftDataStore: DataStore<Preferences> by preferencesDataStore(name = "workout_draft")

/**
 * Persiste el entrenamiento en curso para sobrevivir al cierre de la app o del proceso.
 * Está en el almacenamiento privado de la app (cifrado por Android a nivel de archivo);
 * nunca se sincroniza ni se respalda.
 */
class WorkoutDraftStore(private val context: Context) {
    private val key = stringPreferencesKey("draft_json")

    val draft: Flow<WorkoutDraft?> = context.draftDataStore.data.map { prefs ->
        prefs[key]?.let { json -> runCatching { EventCodec.lenient.decodeFromString(WorkoutDraft.serializer(), json) }.getOrNull() }
    }

    suspend fun save(draft: WorkoutDraft) {
        context.draftDataStore.edit { it[key] = EventCodec.canonical.encodeToString(WorkoutDraft.serializer(), draft) }
    }

    suspend fun clear() {
        context.draftDataStore.edit { it.remove(key) }
    }
}
