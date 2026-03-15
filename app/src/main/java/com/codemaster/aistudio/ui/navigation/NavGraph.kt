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

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "chat/$projectId"
    }
    object Editor : Screen("editor/{projectId}/{fileId}") {
        fun createRoute(projectId: Long, fileId: Long = -1L) = "editor/$projectId/$fileId"
    }
    object Build : Screen("build/{projectId}") {
        fun createRoute(projectId: Long = -1L) = "build/$projectId"
    }
    object Settings : Screen("settings")
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat = { projectId -> navController.navigate(Screen.Chat.createRoute(projectId)) },
                onOpenEditor = { projectId -> navController.navigate(Screen.Editor.createRoute(projectId)) },
                onOpenBuild = { projectId -> navController.navigate(Screen.Build.createRoute(projectId)) },
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
                navArgument("fileId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { back ->
            CodeEditorScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                fileId = back.arguments?.getLong("fileId") ?: -1L,
                onBack = { navController.popBackStack() },
                onOpenChat = { projectId -> navController.navigate(Screen.Chat.createRoute(projectId)) }
            )
        }
        composable(
            Screen.Build.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            BuildScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
