package dev.mjsie.workout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.mjsie.workout.ui.BodyWeightScreen
import dev.mjsie.workout.ui.ExercisesScreen
import dev.mjsie.workout.ui.HistoryScreen
import dev.mjsie.workout.ui.HomeScreen
import dev.mjsie.workout.ui.ScheduleScreen
import dev.mjsie.workout.ui.WeeksScreen
import dev.mjsie.workout.ui.WorkoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutTheme { AppNavigation() }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "This week", Icons.Filled.Home),
    Tab("weeks", "History", Icons.Filled.DateRange),
    Tab("body", "Body weight", Icons.Filled.Person),
)

@Composable
private fun AppNavigation(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    val back: () -> Unit = { nav.popBackStack() }
    val openHistory: (Long) -> Unit = { nav.navigate("history/$it") }
    val openTab: (String) -> Unit = { tabRoute ->
        nav.navigate(tabRoute) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        // Each screen draws its own top bar; this Scaffold only adds the bottom tabs.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { openTab(tab.route) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(inner).consumeWindowInsets(inner)) {
            composable("home") {
                HomeScreen(
                    vm,
                    onOpenSchedule = { nav.navigate("schedule") },
                    onOpenExercises = { nav.navigate("exercises") },
                )
            }
            composable("weeks") {
                WeeksScreen(vm, onOpenWeek = { week -> vm.selectWeek(week); openTab("home") })
            }
            composable("body") {
                BodyWeightScreen(vm)
            }
            composable("schedule") {
                ScheduleScreen(vm, onBack = back)
            }
            composable("exercises") {
                ExercisesScreen(vm, onBack = back, onOpenHistory = openHistory)
            }
            composable("history/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                HistoryScreen(vm, entry.arguments!!.getLong("id"), onBack = back)
            }
        }
    }
}
