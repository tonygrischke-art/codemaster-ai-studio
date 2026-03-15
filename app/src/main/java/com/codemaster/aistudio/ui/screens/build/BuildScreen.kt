package com.codemaster.aistudio.ui.screens.build

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codemaster.aistudio.ui.screens.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    projectId: Long,
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val buildLogs = remember { mutableStateListOf<String>() }
    var buildStatus by remember { mutableStateOf("idle") }

    LaunchedEffect(Unit) {
        buildLogs.add("[CodeMaster] Build system ready.")
        buildLogs.add("[CodeMaster] Configure your GitHub token in Settings to trigger builds.")
        buildLogs.add("[CodeMaster] Repository: github.com/tonygrischke-art/codemaster-ai-studio")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (buildStatus) {
                        "success" -> Color(0xFF1B5E20)
                        "failure" -> MaterialTheme.colorScheme.errorContainer
                        "in_progress" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (buildStatus) {
                            "success" -> Icons.Default.CheckCircle
                            "failure" -> Icons.Default.Error
                            "in_progress" -> Icons.Default.Sync
                            else -> Icons.Default.Circle
                        },
                        contentDescription = null,
                        tint = when (buildStatus) {
                            "success" -> Color(0xFF69F0AE)
                            "failure" -> MaterialTheme.colorScheme.error
                            "in_progress" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        }
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Build Status", fontWeight = FontWeight.Bold)
                        Text(
                            buildStatus.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        buildStatus = "in_progress"
                        buildLogs.add("[${System.currentTimeMillis()}] Triggering GitHub Actions build...")
                        buildLogs.add("Push a commit to main branch or configure workflow_dispatch.")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Trigger Build")
                }
                OutlinedButton(
                    onClick = {
                        buildLogs.clear()
                        buildStatus = "idle"
                        buildLogs.add("[CodeMaster] Logs cleared.")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }

            // Build Info
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Build Configuration", fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    BuildInfoRow("Workflow", "Build CodeMaster AI Studio APK")
                    BuildInfoRow("Branch", "main")
                    BuildInfoRow("Runner", "ubuntu-latest")
                    BuildInfoRow("JDK", "17")
                    BuildInfoRow("Gradle", "8.4")
                    BuildInfoRow("Output", "app-debug.apk")
                }
            }

            // Log Output
            Text("Build Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    buildLogs.forEach { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF00FF41),
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // GitHub Actions Link info
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Monitor live builds at:
github.com/tonygrischke-art/codemaster-ai-studio/actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun BuildInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
