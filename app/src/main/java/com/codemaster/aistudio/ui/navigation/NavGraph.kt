package com.codemaster.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.codemaster.aistudio.ui.screens.build.BuildScreen
import com.codemaster.aistudio.ui.screens.chat.AiChatScreen
import com.codemaster.aistudio.ui.screens.editor.CodeEditorScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Settings : Screen("settings")
    object Chat : Screen("chat/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "chat/$projectId"
    }
    object Editor : Screen("editor/{projectId}/{fileId}") {
        fun createRoute(projectId: Long, fileId: Long = -1L) = "editor/$projectId/$fileId"
    }
    object Build : Screen("build/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "build/$projectId"
    }
    object Terminal : Screen("terminal/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "terminal/$projectId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat     = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenEditor   = { navController.navigate(Screen.Editor.createRoute(it)) },
                onOpenBuild    = { navController.navigate(Screen.Build.createRoute(it)) },
                onOpenTerminal = { navController.navigate(Screen.Terminal.createRoute(it)) },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            Screen.Chat.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            AiChatScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Screen.Editor.route,
            arguments = listOf(
                navArgument("projectId") { type = NavType.LongType },
                navArgument("fileId")    { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            CodeEditorScreen(
                projectId      = back.arguments?.getLong("projectId") ?: -1L,
                fileId         = back.arguments?.getLong("fileId") ?: -1L,
                onBack         = { navController.popBackStack() },
                onOpenChat     = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenTerminal = { navController.navigate(Screen.Terminal.createRoute(it)) }
            )
        }
        composable(
            Screen.Build.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            BuildScreen(
                projectId     = back.arguments?.getLong("projectId") ?: -1L,
                onBack        = { navController.popBackStack() },
                onGoToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            Screen.Terminal.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            TerminalScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack    = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
