package io.gymtracker.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.exerciseName
import io.gymtracker.app.ui.common.Format
import io.gymtracker.app.ui.common.planSummary
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.LoadType
import io.gymtracker.domain.model.SessionId
import io.gymtracker.domain.model.WorkoutSet
import io.gymtracker.domain.scoring.ScoreFormula
import kotlin.math.roundToInt

@Composable
fun SessionDetailScreen(id: SessionId, onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.weeks.asSequence().flatMap { it.items }.firstOrNull { it.session.id == id }
    var savedMessage by remember { mutableStateOf(false) }
    SessionDetailContent(
        item, state.exercises,
        onBack = onBack,
        onDelete = { viewModel.delete(id, onBack) },
        onSaveRoutine = { name -> item?.let { viewModel.saveAsRoutine(name, it.session) { savedMessage = true } } },
        routineSaved = savedMessage,
    )
}

@Composable
fun SessionDetailContent(
    item: SessionItem?,
    exercises: Map<ExerciseId, ExerciseDefinition>,
    onBack: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSaveRoutine: (String) -> Unit = {},
    routineSaved: Boolean = false,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var askRoutineName by remember { mutableStateOf<String?>(null) }
    val defaultRoutineName = item?.let { it.session.routineName ?: stringResource(R.string.routine_default_name, Format.day(it.session.date)) }.orEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
            Text(
                item?.let { Format.day(it.session.date) } ?: stringResource(R.string.session_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (item != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete)) }
            }
        }
        if (item == null) {
            Text(stringResource(R.string.session_not_found), modifier = Modifier.padding(16.dp))
            return
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.session_duration, Format.duration(item.session.durationSeconds)))
                Text(stringResource(R.string.history_volume, item.volumeReps))
                OutlinedButton(
                    onClick = { askRoutineName = defaultRoutineName },
                    enabled = !routineSaved,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(if (routineSaved) R.string.routine_saved else R.string.routine_save_from_session))
                }
            }
            item { ScoreCard(item) }
            item.session.entries.forEach { entry ->
                item {
                    val exercise = exercises[entry.exerciseId]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(exerciseName(entry.exerciseId, exercise), style = MaterialTheme.typography.titleMedium)
                            val loadType = exercise?.loadType ?: LoadType.WEIGHTED
                            planRuns(entry.sets, loadType).forEach { (count, value) ->
                                Text(planSummary(count, value, loadType), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
    askRoutineName?.let { current ->
        AlertDialog(
            onDismissRequest = { askRoutineName = null },
            title = { Text(stringResource(R.string.routine_save_from_session)) },
            text = {
                OutlinedTextField(
                    value = current,
                    onValueChange = { askRoutineName = it.take(60) },
                    label = { Text(stringResource(R.string.routine_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = current.isNotBlank(), onClick = { askRoutineName = null; onSaveRoutine(current) }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { askRoutineName = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.session_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Agrupa series consecutivas con el mismo valor: [10, 10, 10, 8] -> (3, 10), (1, 8). Sin calentamientos. */
private fun planRuns(sets: List<WorkoutSet>, loadType: LoadType): List<Pair<Int, Int>> {
    val values = sets.filter { !it.isWarmup }.map {
        when (loadType) {
            LoadType.WEIGHTED, LoadType.BODYWEIGHT -> it.reps
            LoadType.TIMED -> it.durationSeconds
            LoadType.DISTANCE -> it.distanceMeters
        }
    }
    val runs = mutableListOf<Pair<Int, Int>>()
    for (v in values) {
        val last = runs.lastOrNull()
        if (last != null && last.second == v) runs[runs.lastIndex] = last.first + 1 to v else runs += 1 to v
    }
    return runs
}

@Composable
private fun ScoreCard(item: SessionItem) {
    val score = item.score
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.session_score_title), style = MaterialTheme.typography.titleMedium)
            if (score == null) {
                Text(stringResource(R.string.session_score_not_valid), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            ScoreLine(stringResource(R.string.session_score_base), score.basePoints)
            ScoreLine(stringResource(R.string.session_score_volume), score.volumeBonus)
            ScoreLine(stringResource(R.string.session_score_pr, score.prCount), score.prBonus)
            val multiplied = ((score.basePoints + score.volumeBonus + score.prBonus) * score.multiplier).roundToInt()
            ScoreLine(stringResource(R.string.session_score_streak, score.streakWeeks), multiplied - (score.basePoints + score.volumeBonus + score.prBonus))
            ScoreLine(stringResource(R.string.session_score_goals), score.goalBonus)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.session_score_total), style = MaterialTheme.typography.titleSmall)
                Text("${score.points}", style = MaterialTheme.typography.titleSmall)
            }
            Text("v${ScoreFormula.VERSION}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ScoreLine(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(if (value >= 0) "+$value" else "$value", style = MaterialTheme.typography.bodyMedium)
    }
}
