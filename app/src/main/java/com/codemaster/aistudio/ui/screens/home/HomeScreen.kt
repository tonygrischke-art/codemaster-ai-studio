package com.codemaster.aistudio.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codemaster.aistudio.data.repository.ProjectDirectory

// ─── Language options ──────────────────────────────────────────
private val LANGUAGES = listOf(
    Triple("python", "Python", "🐍"),
    Triple("kotlin", "Kotlin", "🟣"),
    Triple("dart", "Flutter", "💙"),
    Triple("javascript", "JavaScript", "💛"),
    Triple("java", "Java", "☕"),
    Triple("cpp", "C++", "⚙️"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CodeMaster AI Studio") },
                actions = {
                    IconButton(onClick = { viewModel.loadProjects() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToChat,
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                text = { Text("Ask AI") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Quick actions
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickActionCard("💬", "AI Chat", Modifier.weight(1f), onNavigateToChat)
                    QuickActionCard("📝", "Editor", Modifier.weight(1f)) {
                        onNavigateToEditor("")
                    }
                    QuickActionCard("💻", "Terminal", Modifier.weight(1f)) {
                        onNavigateToTerminal("")
                    }
                }
            }

            // Projects header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Projects", style = MaterialTheme.typography.titleMedium)
                        Text(
                            uiState.storageBasePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    FilledTonalButton(
                        onClick = { viewModel.showNewProjectDialog() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }

            // Loading state
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Empty state
            if (!uiState.isLoading && uiState.projects.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📂", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("No projects yet",
                                style = MaterialTheme.typography.titleMedium)
                            Text("Tap New to create your first project",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.showNewProjectDialog() }) {
                                Text("Create First Project")
                            }
                        }
                    }
                }
            }

            // Project list
            items(uiState.projects, key = { it.path }) { project ->
                ProjectCard(
                    project = project,
                    onOpen = { onNavigateToEditor(project.path) },
                    onOpenTerminal = { onNavigateToTerminal(project.path) },
                    onDelete = { viewModel.deleteProject(project.path) }
                )
            }
        }
    }

    // New project dialog
    if (uiState.showNewProjectDialog) {
        NewProjectDialog(
            projectName = uiState.newProjectName,
            selectedLanguage = uiState.newProjectLanguage,
            onNameChange = viewModel::updateNewProjectName,
            onLanguageChange = viewModel::updateNewProjectLanguage,
            onCreate = { viewModel.createProject { project ->
                onNavigateToEditor(project.path)
            }},
            onDismiss = viewModel::hideNewProjectDialog
        )
    }
}

@Composable
fun ProjectCard(
    project: ProjectDirectory,
    onOpen: () -> Unit,
    onOpenTerminal: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val lang = LANGUAGES.find { project.files.any { f -> f.name.endsWith(".${it.first}") ||
            (it.first == "kotlin" && f.name.endsWith(".kt")) ||
            (it.first == "python" && f.name.endsWith(".py")) ||
            (it.first == "javascript" && f.name.endsWith(".js")) } }

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(lang?.third ?: "📁", fontSize = 32.sp)

            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${project.files.size} file${if (project.files.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    project.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open in Editor") },
                        onClick = { onOpen(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Open in Terminal") },
                        onClick = { onOpenTerminal(); showMenu = false },
                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { onDelete(); showMenu = false },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NewProjectDialog(
    projectName: String,
    selectedLanguage: String,
    onNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("New Project", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = projectName,
                    onValueChange = onNameChange,
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(16.dp))
                Text("Language", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LANGUAGES) { (id, name, emoji) ->
                        FilterChip(
                            selected = selectedLanguage == id,
                            onClick = { onLanguageChange(id) },
                            label = { Text("$emoji $name") }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = onCreate,
                        enabled = projectName.isNotBlank()
                    ) { Text("Create") }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    emoji: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(emoji, fontSize = 28.sp)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
