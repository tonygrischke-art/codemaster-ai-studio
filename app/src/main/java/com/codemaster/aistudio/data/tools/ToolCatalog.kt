package com.codemaster.aistudio.data.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

enum class ToolCategory {
    FILE_SYSTEM,
    TERMINAL,
    SEARCH,
    GIT,
    CODE_EXECUTION,
    PROJECT
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val category: ToolCategory,
    val parameters: List<ToolParameter>,
    val requiresProject: Boolean = false
)

data class ToolParameter(
    val name: String,
    val description: String,
    val type: String,
    val required: Boolean = true
)

data class ToolResult(
    val success: Boolean,
    val output: String,
    val error: String? = null
)

data class ToolCall(
    val toolName: String,
    val arguments: Map<String, String>
)

object ToolCatalog {
    
    val allTools: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "read_file",
            description = "Read the contents of a file. Use this to see what's in any file.",
            category = ToolCategory.FILE_SYSTEM,
            parameters = listOf(
                ToolParameter("path", "Full path or relative path to the file", "string", required = true)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "write_file",
            description = "Create or overwrite a file with new content.",
            category = ToolCategory.FILE_SYSTEM,
            parameters = listOf(
                ToolParameter("path", "Full path or relative path for the file", "string", required = true),
                ToolParameter("content", "The content to write to the file", "string", required = true)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "create_directory",
            description = "Create a new directory/folder.",
            category = ToolCategory.FILE_SYSTEM,
            parameters = listOf(
                ToolParameter("path", "Full path or relative path for the new directory", "string", required = true)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "delete_file",
            description = "Delete a file or directory.",
            category = ToolCategory.FILE_SYSTEM,
            parameters = listOf(
                ToolParameter("path", "Path to the file or directory to delete", "string", required = true)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "list_directory",
            description = "List files and folders in a directory.",
            category = ToolCategory.FILE_SYSTEM,
            parameters = listOf(
                ToolParameter("path", "Path to the directory to list", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "search_files",
            description = "Search for files matching a pattern in the project.",
            category = ToolCategory.FILE_SYSTEM,
            parameters = listOf(
                ToolParameter("pattern", "Glob pattern (e.g., *.kt, **/*.java)", "string", required = true),
                ToolParameter("path", "Directory to search in", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "run_command",
            description = "Execute a shell command. Use for git, build tools, npm, etc.",
            category = ToolCategory.TERMINAL,
            parameters = listOf(
                ToolParameter("command", "The command to execute", "string", required = true),
                ToolParameter("cwd", "Working directory (optional)", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "git_status",
            description = "Check git status of the project.",
            category = ToolCategory.GIT,
            parameters = listOf(
                ToolParameter("path", "Path to the git repository", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "git_commit",
            description = "Commit changes to git.",
            category = ToolCategory.GIT,
            parameters = listOf(
                ToolParameter("message", "Commit message", "string", required = true),
                ToolParameter("path", "Path to the git repository", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "git_push",
            description = "Push commits to remote.",
            category = ToolCategory.GIT,
            parameters = listOf(
                ToolParameter("path", "Path to the git repository", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "git_pull",
            description = "Pull changes from remote.",
            category = ToolCategory.GIT,
            parameters = listOf(
                ToolParameter("path", "Path to the git repository", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "search_web",
            description = "Search the web for documentation, errors, or solutions.",
            category = ToolCategory.SEARCH,
            parameters = listOf(
                ToolParameter("query", "What to search for", "string", required = true)
            )
        ),
        
        ToolDefinition(
            name = "get_project_info",
            description = "Get information about the current project structure.",
            category = ToolCategory.PROJECT,
            parameters = listOf()
        ),
        
        ToolDefinition(
            name = "build_project",
            description = "Build the project (gradle, npm, etc.)",
            category = ToolCategory.TERMINAL,
            parameters = listOf(
                ToolParameter("task", "Build task (build, assembleDebug, etc.)", "string", required = false),
                ToolParameter("path", "Path to the project", "string", required = false)
            ),
            requiresProject = true
        ),
        
        ToolDefinition(
            name = "install_dependencies",
            description = "Install dependencies (npm install, pip install, etc.)",
            category = ToolCategory.TERMINAL,
            parameters = listOf(
                ToolParameter("package_manager", "npm, pip, cargo, etc.", "string", required = true),
                ToolParameter("packages", "Packages to install (space separated)", "string", required = false),
                ToolParameter("path", "Path to the project", "string", required = false)
            ),
            requiresProject = true
        )
    )
    
    fun getToolByName(name: String): ToolDefinition? = 
        allTools.find { it.name == name }
    
    fun getToolsByCategory(category: ToolCategory): List<ToolDefinition> =
        allTools.filter { it.category == category }
    
    fun getToolsForProject(): List<ToolDefinition> =
        allTools.filter { it.requiresProject }
    
    fun getGlobalTools(): List<ToolDefinition> =
        allTools.filter { !it.requiresProject }
    
    fun getToolDescriptions(): String {
        return allTools.joinToString("\n") { tool ->
            val params = tool.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
            "- ${tool.name}($params): ${tool.description}"
        }
    }
}