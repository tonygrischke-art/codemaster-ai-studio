package com.codemaster.aistudio.ui.screens.build

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildScreen(
    projectId: Long,
    onBack: () -> Unit,
    onGoToSettings: () -> Unit = {},
    onSendErrorToAi: ((String) -> Unit)? = null,
    viewModel: BuildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val logScrollState = rememberLazyListState()
    var showSetupGuide by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { viewModel.init(projectId) }
    LaunchedEffect(uiState.logs.size) {
        if (uiState.logs.isNotEmpty())
            logScrollState.animateScrollToItem(uiState.logs.size - 1)
    }

    // Send errors to AI when available
    LaunchedEffect(uiState.errorForAi) {
        uiState.errorForAi?.let { error ->
            onSendErrorToAi?.invoke(error)
            viewModel.clearErrorForAi()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { showSetupGuide = !showSetupGuide }) { Icon(Icons.Default.Help, "Guide") }
                    IconButton(onClick = { viewModel.refreshStatus() }) { Icon(Icons.Default.Refresh, "Refresh") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            AnimatedVisibility(visible = showSetupGuide) { SetupGuideCard(onGoToSettings = onGoToSettings) }

            ConfigStatusCard(isConfigured = uiState.isConfigured, owner = uiState.owner, repo = uiState.repo, branch = uiState.branch, onGoToSettings = onGoToSettings)

            // ── Build buttons row ──────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Local Termux build
                Button(
                    onClick = { viewModel.triggerLocalBuild() },
                    enabled = !uiState.isLocalBuilding && !uiState.isTriggering,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    if (uiState.isLocalBuilding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Building...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Local Build", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // GitHub Actions build
                Button(
                    onClick = { viewModel.triggerBuild() },
                    enabled = uiState.isConfigured && !uiState.isTriggering && !uiState.isLocalBuilding,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (uiState.isTriggering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Triggering...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("GitHub Build", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // ── APK Install button ─────────────────────────────────
            AnimatedVisibility(visible = uiState.apkPath != null) {
                uiState.apkPath?.let { apk ->
                    Button(
                        onClick = { viewModel.installApk(apk) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))
                    ) {
                        Icon(Icons.Default.InstallMobile, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("📲 Install APK", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // ── Build errors → AI ──────────────────────────────────
            AnimatedVisibility(visible = uiState.buildErrors.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Build Failed — ${uiState.buildErrors.size} errors", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        uiState.buildErrors.take(3).forEach { err ->
                            Text(err.take(120), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                        if (onSendErrorToAi != null) {
                            Button(
                                onClick = { onSendErrorToAi(uiState.buildErrors.joinToString("\n")) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("🤖 Ask AI to Fix These Errors")
                            }
                        }
                    }
                }
            }

            // ── GitHub latest run ──────────────────────────────────
            uiState.latestRunStatus?.let { status ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(status, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── Live build log ─────────────────────────────────────
            if (uiState.logs.isNotEmpty()) {
                Text("Build Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117)),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    LazyColumn(
                        state = logScrollState,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(uiState.logs) { log ->
                            val color = when {
                                log.contains("✅") || log.contains("SUCCESSFUL") -> Color(0xFF69F0AE)
                                log.contains("❌") || log.contains("FAILED") || log.contains("error:") -> Color(0xFFFF5252)
                                log.contains("warning:") -> Color(0xFFFFD740)
                                else -> Color(0xFF58A6FF)
                            }
                            Text(log, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = color, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupGuideCard(onGoToSettings: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Setup Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()
            SetupStep("1", "Local Build", "Tap 'Local Build' to run ./gradlew assembleDebug directly via Termux. Requires project path set in Settings.")
            SetupStep("2", "GitHub Build", "Requires GitHub token + repo configured in Settings. Triggers cloud build via GitHub Actions.")
            SetupStep("3", "Install APK", "After a successful local build, tap 'Install APK' to install directly on this device.")
            SetupStep("4", "AI Fix Errors", "If build fails, tap 'Ask AI to Fix' to send errors straight to CodeMaster AI.")
            Button(onClick = onGoToSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go to Settings")
            }
        }
    }
}

@Composable
fun SetupStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Text(number, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ConfigStatusCard(isConfigured: Boolean, owner: String, repo: String, branch: String, onGoToSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isConfigured) Color(0xFF1B5E20).copy(alpha = 0.3f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = if (isConfigured) Color(0xFF69F0AE) else MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isConfigured) "GitHub configured ✅" else "GitHub not configured", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(if (isConfigured) "$owner/$repo @ $branch" else "Token, username, and repo required", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onGoToSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
    }
}

@Composable
fun BuildInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
