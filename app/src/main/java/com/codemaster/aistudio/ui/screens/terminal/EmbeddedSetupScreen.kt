package com.codemaster.aistudio.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * One-time setup screen shown while Alpine Linux is being downloaded
 * and extracted. Auto-navigates to the terminal when done.
 */
@Composable
fun EmbeddedSetupScreen(
    onReady: () -> Unit,
    viewModel: EmbeddedSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isDone) {
        if (state.isDone) onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚙️", fontSize = 56.sp)

            Text(
                "Setting Up Terminal",
                fontWeight = FontWeight.Bold,
                fontSize   = 22.sp,
                color      = Color(0xFFE6EDF3)
            )

            Text(
                "CodeMaster is installing its built-in Linux environment.\nThis only happens once.",
                color     = Color(0xFF8B949E),
                fontSize  = 14.sp,
                textAlign = TextAlign.Center
            )

            // Progress bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { (state.progress.coerceAtLeast(0)) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color      = Color(0xFF7C4DFF),
                    trackColor = Color(0xFF21262D)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        state.label,
                        color      = Color(0xFF58A6FF),
                        fontSize   = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (state.progress >= 0) {
                        Text("${state.progress}%", color = Color(0xFF8B949E), fontSize = 12.sp)
                    }
                }
            }

            // Step checklist
            val steps = listOf(
                "Download proot binary"  to (state.progress >= 25),
                "Download Alpine Linux"  to (state.progress >= 70),
                "Extract filesystem"     to (state.progress >= 90),
                "Configure environment"  to (state.progress >= 100)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                steps.forEach { (label, done) ->
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(if (done) "✅" else "⏳", fontSize = 14.sp)
                        Text(
                            label,
                            color    = if (done) Color(0xFF3FB950) else Color(0xFF8B949E),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Error state
            if (state.isFailed) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Setup failed. Check your internet connection and try again.",
                        color     = Color(0xFFFF5252),
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.retry() },
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
                    ) { Text("Retry") }
                }
            }
        }
    }
}
