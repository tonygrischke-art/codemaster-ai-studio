package com.codemaster.aistudio.ui.screens.build

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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
    onGoToSettings: () -> Unit = {},
    viewModel: BuildViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showSetupGuide by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Build", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showSetupGuide = !showSetupGuide }) {
                        Icon(Icons.Default.Help, "Setup guide")
                    }
                    IconButton(onClick = { viewModel.refreshStatus() }) {
                        Icon(Icons.Default.Refresh, "Refresh status")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Setup guide (expandable) ───────────────────────────────
            AnimatedVisibility(visible = showSetupGuide) {
                SetupGuideCard(onGoToSettings = onGoToSettings)
            }

            // ── Config status card ─────────────────────────────────────
            ConfigStatusCard(
                isConfigured = uiState.isConfigured,
                owner = uiState.owner,
                repo = uiState.repo,
                branch = uiState.branch,
                onGoToSettings = onGoToSettings
            )

            // ── BIG TRIGGER BUTTON ─────────────────────────────────────
            Button(
                onClick = { viewModel.triggerBuild() },
                enabled = uiState.isConfigured && !uiState.isTriggering,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isTriggering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Triggering Build...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("🚀 Trigger GitHub Build", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!uiState.isConfigured) {
                Text(
                    "⚠️ Configure GitHub settings first — tap ⚙️ or the Help button above",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // ── Trigger result message ────────────────────────────────
            uiState.triggerMessage?.let { msg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (msg.startsWith("✅"))
                            Color(0xFF1B5E20) else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            if (msg.startsWith("✅")) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (msg.startsWith("✅")) Color(0xFF69F0AE) else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            msg,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // ── Latest run status ──────────────────────────────────────
            uiState.latestRunStatus?.let { status ->
                Card(shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timeline, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Latest Run", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(status, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }

            // ── Build config info ──────────────────────────────────────
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Build Configuration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    BuildInfoRow("Runner",   "ubuntu-latest")
                    BuildInfoRow("JDK",      "17 (Temurin)")
                    BuildInfoRow("Gradle",   "8.4")
                    BuildInfoRow("Output",   "app-debug.apk")
                    BuildInfoRow("Retained", "14 days")
                    BuildInfoRow("Owner",    uiState.owner.ifBlank { "Not set" })
                    BuildInfoRow("Repo",     uiState.repo.ifBlank { "Not set" })
                    BuildInfoRow("Branch",   uiState.branch.ifBlank { "main" })
                }
            }

            // ── Log output ─────────────────────────────────────────────
            if (uiState.logs.isNotEmpty()) {
                Text("Activity Log", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        uiState.logs.forEach { log ->
                            Text(log, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF58A6FF), lineHeight = 16.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Setup Guide Card ───────────────────────────────────────────────────────
@Composable
fun SetupGuideCard(onGoToSettings: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Setup Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider()

            SetupStep(
                number = "1",
                title = "Create GitHub Account",
                description = "Go to github.com and sign up if you haven't already."
            )
            SetupStep(
                number = "2",
                title = "Create a Repository",
                description = "On GitHub: New → Repository name: codemaster-ai-studio → Public → Create. Or use your existing repo."
            )
            SetupStep(
                number = "3",
                title = "Add Workflow File",
                description = "Your repo needs .github/workflows/build.yml — this is already in your repo from the setup we did."
            )
            SetupStep(
                number = "4",
                title = "Create Personal Access Token",
                description = "GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token → Check: repo + workflow scopes → Generate → COPY IT NOW (shown once only)."
            )
            SetupStep(
                number = "5",
                title = "Enter Settings in This App",
                description = "Go to Settings → GitHub section → Enter your token, GitHub username, repo name, and branch (main)."
            )
            SetupStep(
                number = "6",
                title = "Trigger Your First Build",
                description = "Come back here and tap 🚀 Trigger GitHub Build. Watch GitHub Actions at github.com/YOUR_USERNAME/YOUR_REPO/actions"
            )
            SetupStep(
                number = "7",
                title = "Download Your APK",
                description = "When build turns green ✅ → click the run → Artifacts → Download CodeMaster-debug-apk → install on device."
            )

            Button(
                onClick = onGoToSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Go to Settings to Enter Token")
            }
        }
    }
}

@Composable
fun SetupStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
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
fun ConfigStatusCard(
    isConfigured: Boolean,
    owner: String,
    repo: String,
    branch: String,
    onGoToSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured)
                Color(0xFF1B5E20).copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                tint = if (isConfigured) Color(0xFF69F0AE) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isConfigured) "GitHub configured ✅" else "GitHub not configured",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isConfigured) {
                    Text(
                        "$owner/$repo @ $branch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Token, username, and repo required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(onClick = onGoToSettings) {
                Icon(Icons.Default.Settings, "Settings")
            }
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
