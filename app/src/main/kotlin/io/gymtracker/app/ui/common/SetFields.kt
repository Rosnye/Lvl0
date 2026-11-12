package io.gymtracker.app.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.gymtracker.app.R
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.LoadType

/** Campo numérico que acepta solo dígitos (y separador decimal si [decimal]). */
@Composable
fun NumberField(value: String, label: String, modifier: Modifier = Modifier, decimal: Boolean = false, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val filtered = new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(7)
            text = filtered
            onChange(filtered)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Campos de valor de una serie según cómo se mide el ejercicio. Compartido por entrenamiento y rutinas. */
@Composable
fun RowScope.SetValueFields(set: DraftSet, loadType: LoadType, onUpdate: ((DraftSet) -> DraftSet) -> Unit) {
    when (loadType) {
        LoadType.WEIGHTED, LoadType.BODYWEIGHT -> {
            NumberField(
                value = if (set.weightGrams == 0) "" else Format.kg(set.weightGrams),
                label = stringResource(R.string.workout_kg),
                decimal = true,
                modifier = Modifier.weight(1f),
            ) { text ->
                val grams = if (text.isBlank()) 0 else Format.parseKg(text)
                if (grams != null) onUpdate { it.copy(weightGrams = grams) }
            }
            NumberField(
                value = if (set.reps == 0) "" else set.reps.toString(),
                label = stringResource(R.string.workout_reps),
                modifier = Modifier.weight(1f),
            ) { text -> onUpdate { it.copy(reps = text.toIntOrNull() ?: 0) } }
        }
        LoadType.TIMED -> NumberField(
            value = if (set.durationSeconds == 0) "" else set.durationSeconds.toString(),
            label = stringResource(R.string.workout_seconds),
            modifier = Modifier.weight(2f),
        ) { text -> onUpdate { it.copy(durationSeconds = text.toIntOrNull() ?: 0) } }
        LoadType.DISTANCE -> NumberField(
            value = if (set.distanceMeters == 0) "" else set.distanceMeters.toString(),
            label = stringResource(R.string.workout_meters),
            modifier = Modifier.weight(2f),
        ) { text -> onUpdate { it.copy(distanceMeters = text.toIntOrNull() ?: 0) } }
    }
}
