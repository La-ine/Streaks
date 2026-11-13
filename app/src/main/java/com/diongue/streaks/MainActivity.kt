package com.diongue.streaks

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.diongue.streaks.ui.ArchivedScreen
import com.diongue.streaks.ui.HabitDetailScreen
import com.diongue.streaks.ui.HabitListScreen
import com.diongue.streaks.ui.theme.HabitGridTheme

class MainActivity : ComponentActivity() {
    // Activity-scoped so every navigation destination shares the same instance.
    private val viewModel: HabitViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            HabitGridTheme {
                HabitApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coroutine delays don't advance during deep sleep, so re-check the date when returning.
        viewModel.refreshDate()
    }
}

@Composable
fun HabitApp(vm: HabitViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            HabitListScreen(
                vm = vm,
                snackbarHostState = snackbar,
                onOpenHabit = { nav.navigate("habit/$it") },
                onOpenArchived = { nav.navigate("archived") }
            )
        }
        composable(
            "habit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            HabitDetailScreen(
                vm = vm,
                habitId = entry.arguments!!.getLong("id"),
                snackbarHostState = snackbar,
                onBack = { nav.popBackStack("habit/{id}", inclusive = true) }
            )
        }
        composable("archived") {
            ArchivedScreen(
                vm = vm,
                snackbarHostState = snackbar,
                onOpenHabit = { nav.navigate("habit/$it") },
                onBack = { nav.popBackStack() }
            )
        }
    }
}
