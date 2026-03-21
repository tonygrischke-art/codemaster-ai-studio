package com.codemaster.aistudio.ui.screens.dualai

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualAiBuilderScreen(
    onBack: () -> Unit,
    onProjectCreated: (Long) -> Unit,
    viewModel: DualAiBuilderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dual AI Builder", fontWeight = FontWeight.Bold)
                        Text("Claude + Kimi Collaboration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // AI Status indicators
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiStatusChip(
                    label = "Claude",
                    isActive = uiState.hasClaudeKey,
                    activeColor = Color(0xFF7C4DFF),
                    modifier = Modifier.weight(1f)
                )
                AiStatusChip(
                    label = "Kimi",
                    isActive = uiState.hasKimiKey,
                    activeColor = Color(0xFF00BCD4),
                    modifier = Modifier.weight(1f)
                )
                AiStatusChip(
                    label = "Groq",
                    isActive = uiState.hasGroqKey,
                    activeColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
            }

            if (!uiState.hasClaudeKey && !uiState.hasKimiKey) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("Add Claude or Kimi API key in Settings for full dual-AI generation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            // App description input
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Describe Your App", fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = uiState.userDescription,
                        onValueChange = { viewModel.updateDescription(it) },
                        placeholder = { Text("e.g. A social fitness tracker with AI coaching, leaderboards, and health sync") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        enabled = !uiState.isBuilding
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Kotlin", "Python", "JavaScript", "Flutter").forEach { lang ->
                            FilterChip(
                                selected = uiState.language == lang,
                                onClick = { viewModel.updateLanguage(lang) },
                                label = { Text(lang, fontSize = 12.sp) },
                                enabled = !uiState.isBuilding
                            )
                        }
                    }
                }
            }

            // Build button
            Button(
                onClick = { viewModel.startBuild(onProjectCreated) },
                enabled = uiState.userDescription.isNotBlank() && !uiState.isBuilding,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C4DFF)
                )
            ) {
                if (uiState.isBuilding) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Building with Dual AI...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Build with Claude + Kimi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Progress stages
            if (uiState.isBuilding || uiState.progress != null) {
                uiState.progress?.let { progress ->
                    ProgressCard(progress)
                }
            }

            // Results
            if (uiState.isComplete) {
                uiState.progress?.let { progress ->
                    ResultsCard(progress, onBack = onBack, onViewProject = { onProjectCreated(uiState.projectId) })
                }
            }

            // Token usage
            if (uiState.totalTokensUsed > 0) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Session tokens", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${uiState.totalTokensUsed}", fontWeight = FontWeight.Bold)
                        Text(uiState.estimatedCost, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }
            }

            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun AiStatusChip(label: String, isActive: Boolean, activeColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (isActive) activeColor else Color.Gray)
            )
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (isActive) activeColor else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProgressCard(progress: com.codemaster.aistudio.data.repository.DualAiProgress) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(progress.message, fontWeight = FontWeight.Medium)
            }

            val stages = listOf(
                "Starting" to (progress.stage.ordinal >= 0),
                "Claude Vision" to (progress.claudeVision?.isNotBlank() == true),
                "Kimi Vision" to (progress.kimiVision?.isNotBlank() == true),
                "Collaborating" to (progress.mergedSpec?.isNotBlank() == true),
                "Generating Code" to (progress.stage.ordinal >= 5),
                "Writing Files" to (progress.stage.ordinal >= 6)
            )
            stages.forEach { (label, done) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = if (done) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodySmall, color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun ResultsCard(
    progress: com.codemaster.aistudio.data.repository.DualAiProgress,
    onBack: () -> Unit,
    onViewProject: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("App Generated!", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("${progress.generatedFiles.size} files created", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (progress.generatedFiles.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        progress.generatedFiles.keys.take(10).forEach { fileName ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(fileName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (progress.generatedFiles.size > 10) {
                            Text("+ ${progress.generatedFiles.size - 10} more files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Home")
                }
                Button(onClick = onViewProject, modifier = Modifier.weight(2f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open in Editor", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
