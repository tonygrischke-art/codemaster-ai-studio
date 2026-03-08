package com.codemaster.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codemaster.aistudio.ui.screens.chat.AiChatScreen
import com.codemaster.aistudio.ui.screens.editor.CodeEditorScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AiChat : Screen("ai_chat?projectId={projectId}") {
        fun createRoute(projectId: String = "") = "ai_chat?projectId=$projectId"
    }
    object CodeEditor : Screen("editor/{projectId}") {
        fun createRoute(projectId: String) = "editor/$projectId"
    }
    object Terminal : Screen("terminal/{projectId}") {
        fun createRoute(projectId: String) = "terminal/$projectId"
    }
    object Settings : Screen("settings")
}

@Composable
fun CodeMasterNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToChat = { navController.navigate(Screen.AiChat.createRoute()) },
                onNavigateToEditor = { projectId ->
                    navController.navigate(Screen.CodeEditor.createRoute(projectId))
                },
                onNavigateToTerminal = { projectId ->
                    navController.navigate(Screen.Terminal.createRoute(projectId))
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.AiChat.route) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            AiChatScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEditor = { pid ->
                    navController.navigate(Screen.CodeEditor.createRoute(pid))
                }
            )
        }
        composable(Screen.CodeEditor.route) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            CodeEditorScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { navController.navigate(Screen.AiChat.createRoute(projectId)) }
            )
        }
        composable(Screen.Terminal.route) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            TerminalScreen(
                projectId = projectId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
