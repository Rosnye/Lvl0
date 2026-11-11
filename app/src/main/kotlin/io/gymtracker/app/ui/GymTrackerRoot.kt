package io.gymtracker.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import io.gymtracker.app.R
import io.gymtracker.app.ui.history.HistoryScreen
import io.gymtracker.app.ui.history.SessionDetailScreen
import io.gymtracker.app.ui.home.HomeScreen
import io.gymtracker.app.ui.picker.ExercisePickerScreen
import io.gymtracker.app.ui.workout.WorkoutScreen
import io.gymtracker.domain.model.SessionId

enum class Destination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    TRAIN("train", R.string.tab_train, Icons.Filled.Home),
    PROGRESS("progress", R.string.tab_progress, Icons.Filled.Star),
    FRIENDS("friends", R.string.tab_friends, Icons.Filled.Person),
}

object Routes {
    const val WORKOUT = "workout"
    const val PICKER = "picker"
    const val SESSION = "session/{id}"
    fun session(id: SessionId) = "session/${id.value}"
}

/** Raíz con navegación real. */
@Composable
fun GymTrackerRoot() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination
    val selected = Destination.entries.firstOrNull { dest -> current?.hierarchy?.any { it.route == dest.route } == true }
        ?: Destination.TRAIN

    AppScaffold(
        selected = selected,
        onSelect = { dest ->
            navController.navigate(dest.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Destination.TRAIN.route, modifier = Modifier.padding(padding)) {
            composable(Destination.TRAIN.route) {
                HomeScreen(
                    onOpenWorkout = { navController.navigate(Routes.WORKOUT) },
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                )
            }
            composable(Routes.WORKOUT) {
                WorkoutScreen(
                    onAddExercise = { navController.navigate(Routes.PICKER) },
                    onClosed = { navController.popBackStack(Destination.TRAIN.route, inclusive = false) },
                )
            }
            composable(Routes.PICKER) { ExercisePickerScreen(onBack = { navController.popBackStack() }) }
            composable(Destination.PROGRESS.route) { HistoryScreen(onOpenSession = { navController.navigate(Routes.session(it)) }) }
            composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                SessionDetailScreen(id = SessionId(entry.arguments?.getString("id").orEmpty()), onBack = { navController.popBackStack() })
            }
            composable(Destination.FRIENDS.route) { Placeholder() }
        }
    }
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

@Composable
private fun Placeholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.coming_soon))
    }
}
