package io.gymtracker.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.Format
import io.gymtracker.domain.model.SessionId

@Composable
fun HistoryScreen(onOpenSession: (SessionId) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(state, onOpenSession)
}

@Composable
fun HistoryContent(state: HistoryUiState, onOpenSession: (SessionId) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleLarge) }
        if (state.weeks.isEmpty()) {
            item { Text(stringResource(R.string.history_empty)) }
        }
        state.weeks.forEach { week ->
            item(key = "w-${week.weekStart}") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.history_week, Format.day(week.weekStart)), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.history_week_total, week.items.size, week.points), style = MaterialTheme.typography.labelLarge)
                }
            }
            items(week.items, key = { it.session.id.value }) { item ->
                Card(Modifier.fillMaxWidth().clickable { onOpenSession(item.session.id) }) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(item.session.routineName ?: Format.day(item.session.date), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.history_summary, item.session.entries.size, item.session.workingSetCount, Format.duration(item.session.durationSeconds)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(stringResource(R.string.history_volume, item.volumeReps), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.history_points, item.score?.points ?: 0), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
