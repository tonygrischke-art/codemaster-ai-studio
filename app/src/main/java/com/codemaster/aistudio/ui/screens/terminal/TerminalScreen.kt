package com.codemaster.aistudio.ui.screens.terminal

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LineType {
    INPUT,
    OUTPUT,
    ERROR
}

data class TerminalLine(
    val text: String,
    val type: LineType
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit = {}
) {
    var commandInput by remember { mutableStateOf("") }
    var terminalLines by remember { mutableStateOf(listOf<TerminalLine>()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Terminal output
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                reverseLayout = true
            ) {
                items(terminalLines) { line ->
                    val color = when (line.type) {
                        LineType.INPUT -> Color.Green
                        LineType.OUTPUT -> Color.White
                        LineType.ERROR -> Color.Red
                    }
                    
                    Text(
                        text = line.text,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
            
            // Command input
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                label = { Text("Enter command") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )
            
            // Execute button
            Button(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        terminalLines = listOf(
                            TerminalLine("> $commandInput", LineType.INPUT)
                        ) + terminalLines
                        commandInput = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Execute")
            }
        }
    }
}
