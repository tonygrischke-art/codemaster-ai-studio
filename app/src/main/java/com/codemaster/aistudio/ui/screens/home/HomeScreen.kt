package com.codemaster.aistudio.ui.screens.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.ui.components.FileBrowserDialog
import java.text.SimpleDateFormat
import java.util.*

val LANGUAGES = listOf("Kotlin","Java","Python","JavaScript","TypeScript","Rust","Go","C++","Swift","Dart","PHP","Ruby","C","C++","Groovy","Gradle","XML","JSON","YAML")

val ALL_CODE_LANGUAGES = listOf(
    "Kotlin","Java","Python","JavaScript","TypeScript","Rust","Go","C","C++","C#","Swift","Dart","PHP","Ruby","Groovy","Gradle","XML","JSON","YAML","HTML","CSS","SQL","Shell","Bash"
)

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
    val context = LocalContext.current

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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { viewModel.toggleFloatingNotepad(context) },
                    containerColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Icon(Icons.Default.StickyNote2, "Floating Notepad", tint = MaterialTheme.colorScheme.onTertiary)
                }
                SmallFloatingActionButton(
                    onClick = { viewModel.showImportDialog() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.FolderOpen, "Load project", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showNewProjectDialog() },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("New Project") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with recent projects, terminal/upload, and export buttons
            TopActionBar(
                onRecentProjects = { viewModel.showRecentProjectsDialog(context) },
                onTerminal = { uiState.projects.firstOrNull()?.let { onOpenTerminal(it.id) } },
                onUploadCode = { viewModel.showUploadDialog() },
                onExportDoc = { viewModel.exportAsDocument(context) },
                onExportApk = { viewModel.exportAsApk(context) },
                isExportingDoc = uiState.isExportingDoc,
                isExportingApk = uiState.isExportingApk
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Main Code Field - Blank field in the middle
                item {
                    MainCodeField(
                        code = uiState.codeInput,
                        onCodeChange = { viewModel.updateCodeInput(it) },
                        language = uiState.selectedLanguage,
                        onLanguageChange = { viewModel.updateSelectedLanguage(it) },
                        onEdit = { viewModel.editCodeWithAI(context) },
                        onCorrect = { viewModel.correctCodeWithAI(context) },
                        onBuild = { viewModel.buildCode(context) },
                        onRun = { viewModel.runCode(context) },
                        onExportDoc = { viewModel.exportAsDocument(context) },
                        onConvert = { viewModel.convertCodeLanguage(context) },
                        onExportApk = { viewModel.exportAsApk(context) },
                        onExportTemplate = { viewModel.exportAsTemplate(context) },
                        onShare = { viewModel.shareCode(context) },
                        isProcessing = uiState.isProcessingCode,
                        hasProject = uiState.currentProjectId != null
                    )
                }

                // Template Builder Section
                item {
                    TemplateBuilderSection(
                        onUseTemplate = { template -> viewModel.useTemplate(template) },
                        currentTemplate = uiState.selectedTemplate
                    )
                }

                // Recent projects
                if (uiState.projects.isNotEmpty()) {
                    item {
                        Text("Recent Projects", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.projects.take(5)) { project ->
                                RecentProjectCard(
                                    project = project,
                                    onClick = { onOpenEditor(project.id) }
                                )
                            }
                        }
                    }
                }

                // Projects list
                if (uiState.isLoading) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (uiState.projects.isNotEmpty()) {
                    item {
                        Text("All Projects (${uiState.projects.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(uiState.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpenChat = { onOpenChat(project.id) },
                            onOpenEditor = { onOpenEditor(project.id) },
                            onOpenBuild = { onOpenBuild(project.id) },
                            onOpenTerminal = { onOpenTerminal(project.id) },
                            onOpenGit = { onOpenGit(project.id) },
                            onDelete = { viewModel.deleteProject(project) }
                        )
                    }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }

        // Dialogs
        if (uiState.showNewProjectDialog) {
            NewProjectDialog(
                name = uiState.newProjectName,
                language = uiState.newProjectLanguage,
                description = uiState.newProjectDescription,
                onNameChange = viewModel::updateProjectName,
                onLanguageChange = viewModel::updateProjectLanguage,
                onDescriptionChange = viewModel::updateProjectDescription,
                onCreate = { viewModel.createProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideNewProjectDialog
            )
        }

        if (uiState.showImportDialog) {
            ImportProjectDialog(
                path = uiState.importPath,
                onPathChange = viewModel::updateImportPath,
                onBrowse = { viewModel.showFileBrowserDialog() },
                onImport = { viewModel.importProject { id -> onOpenEditor(id) } },
                onDismiss = viewModel::hideImportDialog
            )
        }

        if (uiState.showFileBrowserDialog) {
            FileBrowserDialog(
                onPathSelected = { path ->
                    viewModel.updateImportPath(path)
                    viewModel.hideFileBrowserDialog()
                    viewModel.showImportDialog()
                },
                onDismiss = { viewModel.hideFileBrowserDialog() }
            )
        }

        if (uiState.showUploadDialog) {
            UploadCodeDialog(
                onUpload = { code, lang -> viewModel.loadCode(code, lang) },
                onDismiss = { viewModel.hideUploadDialog() }
            )
        }

        if (uiState.showConvertDialog) {
            ConvertLanguageDialog(
                currentLanguage = uiState.selectedLanguage,
                targetLanguage = uiState.convertTargetLanguage,
                onTargetChange = viewModel::updateConvertTargetLanguage,
                onConvert = { viewModel.convertCodeLanguage(context) },
                onDismiss = viewModel::hideConvertDialog
            )
        }

        if (uiState.showTemplateDialog) {
            TemplateDialog(
                templates = getTemplates(),
                selectedTemplate = uiState.selectedTemplate,
                onSelectTemplate = viewModel::updateSelectedTemplate,
                onUse = { viewModel.applyTemplate(); viewModel.hideTemplateDialog() },
                onDismiss = viewModel::hideTemplateDialog
            )
        }

        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = { viewModel.clearError() }) { Text("Dismiss") } }
            ) { Text(error) }
        }
        } // end Box
    }
}

@Composable
fun TopActionBar(
    onRecentProjects: () -> Unit,
    onTerminal: () -> Unit,
    onUploadCode: () -> Unit,
    onExportDoc: () -> Unit,
    onExportApk: () -> Unit,
    isExportingDoc: Boolean,
    isExportingApk: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Recent projects and Terminal/Upload
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRecentProjects) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Recent", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onUploadCode) {
                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Upload", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onTerminal) {
                    Icon(Icons.Default.Terminal, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Terminal", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Right side - Export buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onExportDoc,
                    enabled = !isExportingDoc,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    if (isExportingDoc) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export Doc", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Button(
                    onClick = onExportApk,
                    enabled = !isExportingApk,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    if (isExportingApk) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Android, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export APK", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    onEdit: () -> Unit,
    onCorrect: () -> Unit,
    onBuild: () -> Unit,
    onRun: () -> Unit,
    onExportDoc: () -> Unit,
    onConvert: () -> Unit,
    onExportApk: () -> Unit,
    onExportTemplate: () -> Unit,
    onShare: () -> Unit,
    isProcessing: Boolean,
    hasProject: Boolean
) {
    var langExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Language selector and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Code Editor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it },
                    modifier = Modifier.width(140.dp)
                ) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                        modifier = Modifier.menuAnchor().height(40.dp),
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        ALL_CODE_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang, style = MaterialTheme.typography.labelSmall) },
                                onClick = { onLanguageChange(lang); langExpanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Code input field - blank field for code
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                if (code.isEmpty()) {
                    Text(
                        "// Paste or type your code here...\n// Python, React Native, Java, C, C++, Kotlin, Groovy, Gradle, Firebase, etc.",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF6B7280),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
                TextField(
                    value = code,
                    onValueChange = onCodeChange,
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color(0xFFE2E8F0),
                        unfocusedTextColor = Color(0xFFE2E8F0),
                        cursorColor = Color(0xFF7C3AED)
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action buttons in two rows
            if (isProcessing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Processing...", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                // First row - Core actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionButton(
                        icon = Icons.Default.Edit,
                        label = "Edit",
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    ActionButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Correct",
                        onClick = onCorrect,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                    ActionButton(
                        icon = Icons.Default.Build,
                        label = "Build",
                        onClick = onBuild,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                    ActionButton(
                        icon = Icons.Default.PlayArrow,
                        label = "Run",
                        onClick = onRun,
                        modifier = Modifier.weight(1f),
                        containerColor = Color(0xFF10B981).copy(alpha = 0.2f)
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Second row - Export and share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionButton(
                        icon = Icons.Default.Description,
                        label = "Export Doc",
                        onClick = onExportDoc,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        icon = Icons.Default.SwapHoriz,
                        label = "Convert",
                        onClick = onConvert,
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        icon = Icons.Default.Android,
                        label = "Export APK",
                        onClick = onExportApk,
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                    ActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        containerColor = Color(0xFF3B82F6).copy(alpha = 0.2f)
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Third row - Template
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ActionButton(
                        icon = Icons.Default.FolderZip,
                        label = "Export Template",
                        onClick = onExportTemplate,
                        modifier = Modifier.weight(1f),
                        containerColor = Color(0xFFF59E0B).copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
fun TemplateBuilderSection(
    onUseTemplate: (CodeTemplate) -> Unit,
    currentTemplate: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Template Builder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onUseTemplate(getTemplates().first()) }) {
                    Text("Browse Templates", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                "Build your app section by section with guided templates",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(getTemplates()) { template ->
                    FilterChip(
                        selected = currentTemplate == template.name,
                        onClick = { onUseTemplate(template) },
                        label = { Text(template.name, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(template.icon, null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }
        }
    }
}

data class CodeTemplate(
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val categories: List<String>
)

fun getTemplates(): List<CodeTemplate> = listOf(
    CodeTemplate("Android App", "Complete Android application with UI", Icons.Default.Android, listOf("Kotlin", "XML")),
    CodeTemplate("React Native", "Cross-platform mobile app", Icons.Default.Web, listOf("JavaScript", "TypeScript")),
    CodeTemplate("Python Script", "Python automation script", Icons.Default.Terminal, listOf("Python")),
    CodeTemplate("Web App", "HTML/CSS/JS web application", Icons.Default.Web, listOf("HTML", "CSS", "JavaScript")),
    CodeTemplate("Firebase", "Firebase backend integration", Icons.Default.Cloud, listOf("JavaScript", "Kotlin")),
    CodeTemplate("API Server", "REST API backend server", Icons.Default.Storage, listOf("Kotlin", "Python", "Go")),
    CodeTemplate("Game", "Simple game application", Icons.Default.SportsEsports, listOf("Dart", "C++")),
    CodeTemplate("ML Model", "Machine learning script", Icons.Default.Insights, listOf("Python"))
)

@Composable
fun RecentProjectCard(
    project: Project,
    onClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    Card(
        modifier = Modifier.width(120.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(project.language.take(2).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(project.name, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateFormat.format(Date(project.lastModified)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadCodeDialog(
    onUpload: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Kotlin") }
    var langExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Code", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Paste your code here") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        ALL_CODE_LANGUAGES.forEach { lang ->
                            DropdownMenuItem(text = { Text(lang) }, onClick = { language = lang; langExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onUpload(code, language) }, enabled = code.isNotBlank()) { Text("Load") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertLanguageDialog(
    currentLanguage: String,
    targetLanguage: String,
    onTargetChange: (String) -> Unit,
    onConvert: () -> Unit,
    onDismiss: () -> Unit
) {
    var langExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert Code Language", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Convert from: $currentLanguage", style = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = it }) {
                    OutlinedTextField(
                        value = targetLanguage,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Convert to") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        ALL_CODE_LANGUAGES.filter { it != currentLanguage }.forEach { lang ->
                            DropdownMenuItem(text = { Text(lang) }, onClick = { onTargetChange(lang); langExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onConvert) { Text("Convert") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TemplateDialog(
    templates: List<CodeTemplate>,
    selectedTemplate: String?,
    onSelectTemplate: (CodeTemplate) -> Unit,
    onUse: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Templates", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(templates) { template ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectTemplate(template) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedTemplate == template.name)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(template.icon, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(template.name, fontWeight = FontWeight.Bold)
                                Text(template.description, style = MaterialTheme.typography.bodySmall)
                                Text(template.categories.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onUse, enabled = selectedTemplate != null) { Text("Use Template") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
                    Text("${project.language} - ${dateFormat.format(Date(project.lastModified))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (project.path.isNotBlank()) {
                        Text(project.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
            if (project.description.isNotBlank() && !project.description.startsWith("Loaded from:")) {
                Spacer(Modifier.height(8.dp))
                Text(project.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ProjectActionButton(Icons.Default.AutoAwesome, "Chat", onOpenChat)
                ProjectActionButton(Icons.Default.Code, "Editor", onOpenEditor)
                ProjectActionButton(Icons.Default.Terminal, "Terminal", onOpenTerminal)
                ProjectActionButton(Icons.Default.AccountTree, "Git", onOpenGit)
                ProjectActionButton(Icons.Default.Build, "Build", onOpenBuild)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectDialog(
    name: String, language: String, description: String,
    onNameChange: (String) -> Unit, onLanguageChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit, onCreate: () -> Unit, onDismiss: () -> Unit
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

@Composable
fun ImportProjectDialog(
    path: String,
    onPathChange: (String) -> Unit,
    onBrowse: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val storageHelper = remember {
        com.codemaster.aistudio.data.util.StorageHelper(context, null)
    }
    val quickPaths = remember { storageHelper.getAvailableLocations() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Load Project", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter path or tap Browse to navigate:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = path, onValueChange = onPathChange,
                    label = { Text("Folder path") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 3,
                    placeholder = { Text("Select a folder below") },
                    trailingIcon = {
                        IconButton(onClick = onBrowse) {
                            Icon(Icons.Default.FolderOpen, "Browse", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
                Text("Quick access:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                quickPaths.take(3).forEach { location ->
                    SuggestionChip(
                        onClick = { onPathChange(location.path) }, 
                        label = { Text(location.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onImport, enabled = path.isNotBlank()) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Load")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}