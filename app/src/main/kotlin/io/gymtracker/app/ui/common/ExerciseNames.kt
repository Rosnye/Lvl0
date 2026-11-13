package io.gymtracker.app.ui.common

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.gymtracker.domain.model.BuiltInExercises
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId

/**
 * Nombre visible de un ejercicio en el idioma de la app. Los del catálogo precargado se traducen
 * con el recurso `ex_<clave>`; los personalizados muestran el nombre que escribió el usuario.
 */
@SuppressLint("DiscouragedApi") // búsqueda por nombre: 54 recursos con clave estable, protegidos en res/raw/keep.xml
fun exerciseName(context: Context, id: ExerciseId, definition: ExerciseDefinition?): String {
    if (BuiltInExercises.isBuiltIn(id)) {
        val resId = context.resources.getIdentifier("ex_" + id.value.removePrefix("builtin:"), "string", context.packageName)
        if (resId != 0) return context.getString(resId)
    }
    return definition?.name ?: id.value
}

@Composable
fun exerciseName(id: ExerciseId, definition: ExerciseDefinition?): String {
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales
    return remember(id, definition, locales) { exerciseName(context, id, definition) }
}
