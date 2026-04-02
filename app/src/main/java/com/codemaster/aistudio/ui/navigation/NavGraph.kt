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
import com.codemaster.aistudio.ui.screens.git.GitScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.preview.PreviewScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen
import com.codemaster.aistudio.ui.screens.snippets.SnippetScreen
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen
import com.codemaster.aistudio.ui.screens.terminal.EmbeddedSetupScreen

sealed class Screen(val route: String) {
    object Home     : Screen("home")
    object Settings : Screen("settings")
    object Snippets : Screen("snippets")
    object Chat     : Screen("chat/{projectId}")          { fun createRoute(id: Long = -1L) = "chat/$id" }
    object Editor   : Screen("editor/{projectId}/{fileId}") { fun createRoute(pid: Long, fid: Long = -1L) = "editor/$pid/$fid" }
    object Build    : Screen("build/{projectId}")         { fun createRoute(id: Long = -1L) = "build/$id" }
    object Terminal : Screen("terminal/{projectId}")      { fun createRoute(id: Long = -1L) = "terminal/$id" }
    object TerminalSetup : Screen("terminal_setup/{projectId}") { fun createRoute(id: Long = -1L) = "terminal_setup/$id" }
    object Git      : Screen("git/{projectId}")           { fun createRoute(id: Long = -1L) = "git/$id" }
    object Preview  : Screen("preview/{projectId}")       { fun createRoute(id: Long = -1L) = "preview/$id" }
}

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onOpenChat      = { navController.navigate(Screen.Chat.createRoute(it)) },
                onOpenEditor    = { navController.navigate(Screen.Editor.createRoute(it)) },
                onOpenBuild     = { navController.navigate(Screen.Build.createRoute(it)) },
                onOpenTerminal  = { navController.navigate(Screen.TerminalSetup.createRoute(it)) },
                onOpenGit       = { navController.navigate(Screen.Git.createRoute(it)) },
                onOpenSnippets  = { navController.navigate(Screen.Snippets.route) },
                onOpenSettings  = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Snippets.route) {
            SnippetScreen(onBack = { navController.popBackStack() })
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
                onOpenTerminal = { navController.navigate(Screen.TerminalSetup.createRoute(it)) }
            )
        }

        composable(
            Screen.Build.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            val projectId = back.arguments?.getLong("projectId") ?: -1L
            BuildScreen(
                projectId      = projectId,
                onBack         = { navController.popBackStack() },
                onGoToSettings = { navController.navigate(Screen.Settings.route) },
                onSendErrorToAi = { error ->
                    navController.navigate(Screen.Chat.createRoute(projectId))
                }
            )
        }

        composable(
            Screen.Terminal.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            TerminalScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.TerminalSetup.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            val projectId = back.arguments?.getLong("projectId") ?: -1L
            EmbeddedSetupScreen(
                projectId = projectId,
                onComplete = { navController.navigate(Screen.Terminal.createRoute(projectId)) { popUpTo(Screen.TerminalSetup.route) { inclusive = true } } },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Screen.Git.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            GitScreen(
                projectId = back.arguments?.getLong("projectId") ?: -1L,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            Screen.Preview.route,
            arguments = listOf(navArgument("projectId") { type = NavType.LongType; defaultValue = -1L })
        ) { back ->
            // PreviewScreen needs fileName + content passed via SavedStateHandle
            // For now navigate back if no content
            navController.popBackStack()
        }
    }
}
