package io.gymtracker.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.gymtracker.app.R
import io.gymtracker.domain.model.DraftSet
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.SetPlan

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

/** Etiqueta del valor de cada serie según cómo se mide el ejercicio. */
@Composable
fun planValueLabel(loadType: LoadType): String = stringResource(
    when (loadType) {
        LoadType.WEIGHTED, LoadType.BODYWEIGHT -> R.string.plan_reps
        LoadType.TIMED -> R.string.plan_seconds
        LoadType.DISTANCE -> R.string.plan_meters
    },
)

/**
 * Editor de series × repeticiones (o segundos / metros): se rellena una vez por ejercicio.
 * [onChange] recibe la nueva lista de series, conservando cuáles estaban hechas.
 */
@Composable
fun SetPlanEditor(sets: List<DraftSet>, loadType: LoadType, onChange: (List<DraftSet>) -> Unit) {
    val count = sets.size
    val value = SetPlan.valueOf(sets, loadType)
    val step = when (loadType) {
        LoadType.WEIGHTED, LoadType.BODYWEIGHT -> 1
        LoadType.TIMED -> 5
        LoadType.DISTANCE -> 100
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        Stepper(
            label = stringResource(R.string.plan_sets),
            value = count,
            onValue = { onChange(SetPlan.resize(sets, it, value, loadType)) },
            min = 1,
            max = SetPlan.MAX_SETS,
            step = 1,
        )
        Text("×", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 14.dp))
        Stepper(
            label = planValueLabel(loadType),
            value = value,
            onValue = { onChange(SetPlan.resize(sets, count, it, loadType)) },
            min = 0,
            max = SetPlan.MAX_VALUE,
            step = step,
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, onValue: (Int) -> Unit, min: Int, max: Int, step: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onValue((value - step).coerceAtLeast(min)) }, enabled = value > min) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            var text by remember(value) { mutableStateOf(value.toString()) }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new.filter { it.isDigit() }.take(4)
                    text.toIntOrNull()?.let { onValue(it.coerceIn(min, max)) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp),
            )
            FilledTonalIconButton(onClick = { onValue((value + step).coerceAtMost(max)) }, enabled = value < max) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        }
    }
}

/** Texto compacto de un plan: "4 × 10 reps", "3 × 30 s", "1 × 1000 m". */
@Composable
fun planSummary(sets: Int, value: Int, loadType: LoadType): String = when (loadType) {
    LoadType.WEIGHTED, LoadType.BODYWEIGHT -> stringResource(R.string.plan_summary_reps, sets, value)
    LoadType.TIMED -> stringResource(R.string.plan_summary_seconds, sets, value)
    LoadType.DISTANCE -> stringResource(R.string.plan_summary_meters, sets, value)
}
