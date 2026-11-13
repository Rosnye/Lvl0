package io.gymtracker.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.gymtracker.app.R
import io.gymtracker.app.ui.friends.FriendsScreen
import io.gymtracker.app.ui.friends.IncomingPacketDialog
import io.gymtracker.app.ui.goal.NewStrengthGoalScreen
import io.gymtracker.app.ui.history.HistoryScreen
import io.gymtracker.app.ui.history.SessionDetailScreen
import io.gymtracker.app.ui.home.HomeScreen
import io.gymtracker.app.ui.picker.ExercisePickerScreen
import io.gymtracker.app.ui.picker.ExercisePickerViewModel
import io.gymtracker.app.ui.progress.ProgressScreen
import io.gymtracker.app.ui.routine.RoutineEditorScreen
import io.gymtracker.app.ui.routine.RoutineEditorViewModel
import io.gymtracker.app.ui.workout.WorkoutScreen
import io.gymtracker.app.ui.workout.WorkoutViewModel
import io.gymtracker.domain.model.ExerciseId
import io.gymtracker.domain.model.SessionId

enum class Destination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    PROGRESS("progress", R.string.tab_progress, Icons.Filled.Star),
    TRAIN("train", R.string.tab_train, Icons.Filled.PlayArrow),
    FRIENDS("friends", R.string.tab_friends, Icons.Filled.Person),
}

object Routes {
    const val WORKOUT = "workout"
    const val PICKER = "picker?${ExercisePickerViewModel.ARG_GOAL_MODE}={${ExercisePickerViewModel.ARG_GOAL_MODE}}"
    const val SESSION = "session/{id}"
    const val HISTORY = "history"
    const val ROUTINE = "routine/{${RoutineEditorViewModel.ARG_ID}}"
    const val NEW_GOAL = "goal/strength"

    fun picker(goalMode: Boolean = false) = "picker?${ExercisePickerViewModel.ARG_GOAL_MODE}=$goalMode"
    fun session(id: SessionId) = "session/${id.value}"
    fun routine(id: String?) = "routine/${id ?: RoutineEditorViewModel.NEW}"

    /** Pestaña que se marca en la barra inferior para cada pantalla. */
    fun tabOf(route: String?): Destination = when {
        route == null -> Destination.PROGRESS
        route.startsWith(Destination.PROGRESS.route) || route == HISTORY || route.startsWith("session") || route == NEW_GOAL -> Destination.PROGRESS
        route.startsWith(Destination.FRIENDS.route) -> Destination.FRIENDS
        else -> Destination.TRAIN
    }
}

/** Clave con la que el selector devuelve el ejercicio elegido a la pantalla anterior. */
private const val PICKED_EXERCISE = "picked_exercise"

/** Raíz con navegación real. */
@Composable
fun GymTrackerRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()

    AppScaffold(
        selected = Routes.tabOf(backStack?.destination?.route),
        onSelect = { dest ->
            navController.navigate(dest.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Destination.PROGRESS.route, modifier = Modifier.padding(padding)) {
            composable(Destination.TRAIN.route) {
                HomeScreen(
                    onOpenWorkout = { navController.navigate(Routes.WORKOUT) },
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                    onEditRoutine = { navController.navigate(Routes.routine(it?.value)) },
                )
            }
            composable(Routes.WORKOUT) { entry ->
                val viewModel: WorkoutViewModel = hiltViewModel()
                val picked = entry.pickedExercise()
                androidx.compose.runtime.LaunchedEffect(picked) {
                    if (picked != null) {
                        viewModel.addExercise(picked)
                        entry.clearPicked()
                    }
                }
                WorkoutScreen(
                    onAddExercise = { navController.navigate(Routes.picker()) },
                    onClosed = { navController.popBackStack(Destination.TRAIN.route, inclusive = false) },
                    viewModel = viewModel,
                )
            }
            composable(
                Routes.PICKER,
                arguments = listOf(navArgument(ExercisePickerViewModel.ARG_GOAL_MODE) { type = NavType.BoolType; defaultValue = false }),
            ) {
                ExercisePickerScreen(
                    onPicked = { id -> navController.returnPicked(id) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ROUTINE, arguments = listOf(navArgument(RoutineEditorViewModel.ARG_ID) { type = NavType.StringType })) { entry ->
                RoutineEditorScreen(
                    pickedExercise = entry.pickedExercise(),
                    onPickedConsumed = { entry.clearPicked() },
                    onAddExercise = { navController.navigate(Routes.picker()) },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(Destination.PROGRESS.route) {
                ProgressScreen(
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onNewStrengthGoal = { navController.navigate(Routes.NEW_GOAL) },
                )
            }
            composable(Routes.NEW_GOAL) { entry ->
                NewStrengthGoalScreen(
                    pickedExercise = entry.pickedExercise(),
                    onPickedConsumed = { entry.clearPicked() },
                    onChooseExercise = { navController.navigate(Routes.picker(goalMode = true)) },
                    onClose = { navController.popBackStack() },
                )
            }
            composable(Routes.HISTORY) { HistoryScreen(onOpenSession = { navController.navigate(Routes.session(it)) }) }
            composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                SessionDetailScreen(id = SessionId(entry.arguments?.getString("id").orEmpty()), onBack = { navController.popBackStack() })
            }
            composable(Destination.FRIENDS.route) { FriendsScreen() }
        }
    }

    IncomingPacketDialog(onViewBoard = {
        navController.navigate(Destination.FRIENDS.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
    })
}

@Composable
private fun NavBackStackEntry.pickedExercise(): ExerciseId? {
    val value by savedStateHandle.getStateFlow<String?>(PICKED_EXERCISE, null).collectAsStateWithLifecycle()
    return value?.let(::ExerciseId)
}

private fun NavBackStackEntry.clearPicked() {
    savedStateHandle[PICKED_EXERCISE] = null
}

private fun NavHostController.returnPicked(id: ExerciseId) {
    previousBackStackEntry?.savedStateHandle?.set(PICKED_EXERCISE, id.value)
    popBackStack()
}

/** Andamiaje visual (barra inferior) sin dependencia de navegación: reutilizable en previews y capturas. */
@Composable
fun AppScaffold(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = dest == selected,
                        onClick = { onSelect(dest) },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
        content = content,
    )
}
