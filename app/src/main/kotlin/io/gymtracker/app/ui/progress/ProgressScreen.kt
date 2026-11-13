package io.gymtracker.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.gymtracker.app.R
import io.gymtracker.app.ui.common.Format
import io.gymtracker.app.ui.common.descriptionRes
import io.gymtracker.app.ui.common.emoji
import io.gymtracker.app.ui.common.titleRes
import io.gymtracker.domain.achievements.Achievement
import io.gymtracker.domain.achievements.AchievementId
import io.gymtracker.domain.goals.FrequencyProgress
import io.gymtracker.domain.goals.StrengthProgress
import io.gymtracker.domain.model.ExerciseDefinition
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.GoalId
import io.gymtracker.domain.scoring.ScoreFormula
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

@Composable
fun ProgressScreen(
    onOpenHistory: () -> Unit,
    onNewStrengthGoal: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgressContent(
        state = state,
        onOpenHistory = onOpenHistory,
        onNewStrengthGoal = onNewStrengthGoal,
        onPrevMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onWeeklyFrequency = viewModel::setWeeklyFrequency,
        onDeleteGoal = viewModel::deleteGoal,
    )
}

@Composable
fun ProgressContent(
    state: ProgressUiState,
    onOpenHistory: () -> Unit = {},
    onNewStrengthGoal: () -> Unit = {},
    onPrevMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onWeeklyFrequency: (Int) -> Unit = {},
    onDeleteGoal: (GoalId) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LevelCard(state)
        CalendarCard(state.calendar, onPrevMonth, onNextMonth)

        Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.titleLarge)
        state.goals?.let { goals ->
            FrequencyCard(goals.frequency, onWeeklyFrequency)
            goals.activeStrength.forEach { StrengthGoalCard(it, state.exercises, onDeleteGoal) }
            if (goals.activeStrength.isEmpty()) Text(stringResource(R.string.goal_none), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onNewStrengthGoal, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.goal_add_strength)) }
            if (goals.achievedStrength.isNotEmpty()) {
                Text(stringResource(R.string.goal_achieved_title), style = MaterialTheme.typography.titleMedium)
                goals.achievedStrength.forEach { StrengthGoalCard(it, state.exercises, onDeleteGoal) }
            }
        }

        BadgesSection(state.achievements)

        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.progress_history)) }
    }
}

@Composable
private fun LevelCard(state: ProgressUiState) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.progress_level, state.level), style = MaterialTheme.typography.headlineMedium)
                Text("🔥 " + stringResource(R.string.progress_streak, state.streakWeeks), style = MaterialTheme.typography.titleSmall)
            }
            LinearProgressIndicator(progress = { state.levelFraction }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.progress_total, state.totalPoints), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.progress_next_level, state.nextLevelPoints - state.totalPoints, state.level + 1), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CalendarCard(calendar: MonthCalendar, onPrev: () -> Unit, onNext: () -> Unit) {
    val locale = Format.appLocale()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_prev)) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        calendar.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.calendar_month_sessions, calendar.sessionDays), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onNext, enabled = calendar.month < java.time.YearMonth.from(calendar.today)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { dow ->
                    Text(
                        dow.getDisplayName(TextStyle.NARROW, locale),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            val first = calendar.month.atDay(1)
            val leading = first.dayOfWeek.value - 1 // semana empieza en lunes
            val days = calendar.month.lengthOfMonth()
            val cells = leading + days
            val rows = (cells + 6) / 7
            for (row in 0 until rows) {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNumber = row * 7 + col - leading + 1
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                            if (dayNumber in 1..days) {
                                val date = calendar.month.atDay(dayNumber)
                                val trained = date in calendar.trainedDays
                                val isToday = date == calendar.today
                                Box(
                                    Modifier.fillMaxSize().clip(CircleShape).background(
                                        when {
                                            trained -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        },
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$dayNumber",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (trained || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (trained) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyCard(progress: FrequencyProgress, onChange: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.goal_frequency_title), style = MaterialTheme.typography.titleMedium)
            Text(
                if (progress.reached) stringResource(R.string.goal_frequency_reached)
                else stringResource(R.string.goal_frequency_progress, progress.thisWeek, progress.target),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(if (progress.goal == null) R.string.goal_frequency_default else R.string.goal_frequency_change),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (ScoreFormula.MIN_WEEKLY_TARGET..ScoreFormula.MAX_WEEKLY_TARGET).forEach { n ->
                    FilterChip(selected = progress.target == n, onClick = { if (progress.target != n) onChange(n) }, label = { Text("$n") })
                }
            }
        }
    }
}

@Composable
private fun StrengthGoalCard(progress: StrengthProgress, exercises: Map<ExerciseId, ExerciseDefinition>, onDelete: (GoalId) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    val name = exercises[progress.exerciseId]?.name ?: progress.exerciseId.value
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (progress.achieved) "🎯 " else "") + if (progress.isRepsGoal) stringResource(R.string.goal_strength_reps, name, progress.minReps)
                    else stringResource(R.string.goal_strength_weight, name, Format.kg(progress.targetWeightGrams), progress.minReps),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!progress.achieved) {
                    IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.goal_delete)) }
                }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                val achievedOn = progress.achievedOn
                Text(
                    when {
                        achievedOn != null -> stringResource(R.string.goal_strength_achieved, Format.day(achievedOn))
                        progress.isRepsGoal -> stringResource(R.string.goal_strength_best_reps, progress.bestReps)
                        else -> stringResource(R.string.goal_strength_best_weight, Format.kg(progress.bestWeightGrams), progress.minReps)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(R.string.goal_delete_confirm)) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete(progress.goal.id) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BadgesSection(achievements: List<Achievement>) {
    var selected by remember { mutableStateOf<Achievement?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.badges_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.badges_count, achievements.count { it.unlocked }, achievements.size), style = MaterialTheme.typography.labelLarge)
    }
    achievements.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { badge ->
                Card(
                    onClick = { selected = badge },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (badge.unlocked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(badge.id.emoji(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.alpha(if (badge.unlocked) 1f else 0.3f))
                        Text(stringResource(badge.id.titleRes()), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                        if (!badge.unlocked) {
                            LinearProgressIndicator(progress = { badge.fraction }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
    }
    selected?.let { badge ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${badge.id.emoji()}  ${stringResource(badge.id.titleRes())}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(badge.id.descriptionRes()))
                    val unlockedOn = badge.unlockedOn
                    Text(
                        if (unlockedOn != null) stringResource(R.string.badge_unlocked_on, Format.day(unlockedOn))
                        else stringResource(R.string.badge_progress, badgeValue(badge.id, badge.progress), badgeValue(badge.id, badge.target)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.summary_ok)) } },
        )
    }
}

private fun badgeValue(id: AchievementId, value: Int): String = when (id) {
    AchievementId.REPS_1K, AchievementId.REPS_10K -> "%,d reps".format(value)
    AchievementId.LONG_SESSION -> "$value min"
    else -> "$value"
}
