package com.codemaster.aistudio.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.Project
import java.text.SimpleDateFormat
import java.util.*

val LANGUAGES = listOf("Kotlin","Java","Python","JavaScript","TypeScript","Rust","Go","C++","Swift","Dart","PHP","Ruby")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenChat: (Long) -> Unit,
    onOpenEditor: (Long) -> Unit,
    onOpenBuild: (Long) -> Unit,
    onOpenTerminal: (Long) -> Unit,
    onOpenGit: (Long) -> Unit,
    onOpenSnippets: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("CodeMaster AI", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSnippets) { Icon(Icons.Default.Bookmark, "Snippets") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showNewProjectDialog() },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Project") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.projects.isEmpty() -> EmptyProjectsPlaceholder(modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Text("Projects (${uiState.projects.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(uiState.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpenChat     = { onOpenChat(project.id) },
                            onOpenEditor   = { onOpenEditor(project.id) },
                            onOpenBuild    = { onOpenBuild(project.id) },
                            onOpenTerminal = { onOpenTerminal(project.id) },
                            onOpenGit      = { onOpenGit(project.id) },
                            onDelete       = { viewModel.deleteProject(project) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            uiState.error?.let { error ->
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }) { Text(error) }
            }
        }

        if (uiState.showNewProjectDialog) {
            NewProjectDialog(
                name = uiState.newProjectName, language = uiState.newProjectLanguage, description = uiState.newProjectDescription,
                onNameChange = viewModel::updateProjectName, onLanguageChange = viewModel::updateProjectLanguage, onDescriptionChange = viewModel::updateProjectDescription,
                onCreate = { viewModel.createProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideNewProjectDialog
            )
        }
    }
}

@Composable
fun ProjectCard(
    project: Project,
    onOpenChat: () -> Unit, onOpenEditor: () -> Unit, onOpenBuild: () -> Unit,
    onOpenTerminal: () -> Unit, onOpenGit: () -> Unit, onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))),
                    contentAlignment = Alignment.Center
                ) { Text(project.language.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${project.language} • ${dateFormat.format(Date(project.lastModified))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
            if (project.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(project.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProjectActionButton(Icons.Default.ChatBubbleOutline, "Chat",     onOpenChat)
                ProjectActionButton(Icons.Default.Code,              "Editor",   onOpenEditor)
                ProjectActionButton(Icons.Default.Terminal,          "Terminal", onOpenTerminal)
                ProjectActionButton(Icons.Default.AccountTree,       "Git",      onOpenGit)
                ProjectActionButton(Icons.Default.Build,             "Build",    onOpenBuild)
            }
        }
    }
}

@Composable
fun ProjectActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EmptyProjectsPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
        Text("No projects yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("Tap + to create your first project", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    name: String, language: String, description: String,
    onNameChange: (String) -> Unit, onLanguageChange: (String) -> Unit, onDescriptionChange: (String) -> Unit,
    onCreate: () -> Unit, onDismiss: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = onNameChange, label = { Text("Project Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                    OutlinedTextField(value = language, onValueChange = {}, readOnly = true, label = { Text("Language") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) }, modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        LANGUAGES.forEach { lang -> DropdownMenuItem(text = { Text(lang) }, onClick = { onLanguageChange(lang); langExpanded = false }) }
                    }
                }
                OutlinedTextField(value = description, onValueChange = onDescriptionChange, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
            }
        },
        confirmButton = { Button(onClick = onCreate, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
