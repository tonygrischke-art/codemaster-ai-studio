package com.codemaster.aistudio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.codemaster.aistudio.ui.screens.build.BuildScreen
import com.codemaster.aistudio.ui.screens.chat.AiChatScreen
import com.codemaster.aistudio.ui.screens.editor.CodeEditorScreen
import com.codemaster.aistudio.ui.screens.home.HomeScreen
import com.codemaster.aistudio.ui.screens.settings.SettingsScreen
import com.codemaster.aistudio.ui.screens.terminal.TerminalScreen
import java.net.URLDecoder
import java.net.URLEncoder

// ─── Routes ───────────────────────────────────────────────────
object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{projectPath}"
    const val CHAT = "chat?projectPath={projectPath}&file={file}"
    const val TERMINAL = "terminal/{projectPath}"
    const val SETTINGS = "settings"
    const val BUILD = "build"

    fun editor(projectPath: String) = "editor/${URLEncoder.encode(projectPath, "UTF-8")}"
    fun chat(projectPath: String = "", file: String = "") =
        "chat?projectPath=${URLEncoder.encode(projectPath, "UTF-8")}&file=${URLEncoder.encode(file, "UTF-8")}"
    fun terminal(projectPath: String) = "terminal/${URLEncoder.encode(projectPath, "UTF-8")}"
}

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        // ─── Home ──────────────────────────────────────────────
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToChat = {
                    navController.navigate(Routes.chat())
                },
                onNavigateToEditor = { projectPath ->
                    navController.navigate(Routes.editor(projectPath))
                },
                onNavigateToTerminal = { projectPath ->
                    navController.navigate(Routes.terminal(projectPath))
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        // ─── Editor ────────────────────────────────────────────
        composable(Routes.EDITOR) { backStack ->
            val projectPath = URLDecoder.decode(
                backStack.arguments?.getString("projectPath") ?: "",
                "UTF-8"
            )
            CodeEditorScreen(
                projectId = projectPath,
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = {
                    navController.navigate(Routes.chat(projectPath))
                }
            )
        }

        // ─── AI Chat ───────────────────────────────────────────
        composable(Routes.CHAT) { backStack ->
            val projectPath = URLDecoder.decode(
                backStack.arguments?.getString("projectPath") ?: "",
                "UTF-8"
            )
            val file = URLDecoder.decode(
                backStack.arguments?.getString("file") ?: "",
                "UTF-8"
            )
            AiChatScreen(
                projectPath = projectPath,
                currentFile = file,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── Terminal ──────────────────────────────────────────
        composable(Routes.TERMINAL) { backStack ->
            val projectPath = URLDecoder.decode(
                backStack.arguments?.getString("projectPath") ?: "",
                "UTF-8"
            )
            TerminalScreen(
                projectId = projectPath,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ─── Settings ──────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBuild = {
                    navController.navigate(Routes.BUILD)
                }
            )
        }

        // ─── Build ─────────────────────────────────────────────
        composable(Routes.BUILD) {
            BuildScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
