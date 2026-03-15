package com.codemaster.aistudio.ui.screens.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

// Terminal color palette (VS Code dark terminal style)
private val TermBg       = Color(0xFF0D1117)
private val TermGreen    = Color(0xFF3FB950)
private val TermRed      = Color(0xFFF85149)
private val TermYellow   = Color(0xFFD29922)
private val TermBlue     = Color(0xFF58A6FF)
private val TermCyan     = Color(0xFF39C5CF)
private val TermGray     = Color(0xFF8B949E)
private val TermWhite    = Color(0xFFE6EDF3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    projectId: Long,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) { viewModel.init(projectId) }

    // Auto-scroll to bottom on new output
    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(uiState.lines.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Terminal,
                            null,
                            tint = TermGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Terminal", fontWeight = FontWeight.Bold, color = TermWhite)
                            Text(
                                uiState.cwd.takeLast(40),
                                style = MaterialTheme.typography.labelSmall,
                                color = TermGray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = TermWhite)
                    }
                },
                actions = {
                    // Running indicator
                    if (uiState.isRunning) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(TermGreen, shape = RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("LIVE", color = TermGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = {
                        scope.launch { viewModel.submitCommand() }
                    }) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = TermWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
            )
        },
        containerColor = TermBg,
        bottomBar = {
            TerminalInputBar(
                input = uiState.input,
                onInputChange = { viewModel.updateInput(it) },
                onSubmit = { viewModel.submitCommand() },
                onCtrlC = { viewModel.sendCtrlC() },
                onCtrlD = { viewModel.sendCtrlD() },
                onTab = { viewModel.sendTab() },
                onHistoryUp = { viewModel.historyUp() },
                onHistoryDown = { viewModel.historyDown() },
                isRunning = uiState.isRunning
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TermBg)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                items(uiState.lines) { line ->
                    TerminalLineView(line)
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun TerminalLineView(line: TerminalLine) {
    val color = when (line.type) {
        LineType.INPUT  -> TermCyan
        LineType.OUTPUT -> TermWhite
        LineType.ERROR  -> TermRed
        LineType.SYSTEM -> TermYellow
    }
    val prefix = when (line.type) {
        LineType.INPUT  -> ""    // already has "$ " prefix from viewmodel
        LineType.OUTPUT -> ""
        LineType.ERROR  -> ""
        LineType.SYSTEM -> ""
    }

    Text(
        text = "$prefix${line.text}",
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TerminalInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCtrlC: () -> Unit,
    onCtrlD: () -> Unit,
    onTab: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    isRunning: Boolean
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF161B22))
            .navigationBarsPadding()
    ) {
        // Quick-action keys row (mobile keyboard helpers)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF21262D))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TermKeyButton("Tab", onClick = onTab)
            TermKeyButton("↑", onClick = onHistoryUp)
            TermKeyButton("↓", onClick = onHistoryDown)
            TermKeyButton("^C", onClick = onCtrlC, color = TermRed)
            TermKeyButton("^D", onClick = onCtrlD, color = TermYellow)
            TermKeyButton("~", onClick = { onInputChange("$input~") })
            TermKeyButton("/", onClick = { onInputChange("$input/") })
            TermKeyButton("-", onClick = { onInputChange("$input-") })
            TermKeyButton("|", onClick = { onInputChange("$input|") })
            TermKeyButton("&&", onClick = { onInputChange("$input && ") })
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ ",
                color = TermGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            TextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TermWhite
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = TermGreen,
                    focusedTextColor = TermWhite,
                    unfocusedTextColor = TermWhite
                ),
                placeholder = {
                    Text("Enter command...", color = TermGray, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() })
            )
            IconButton(
                onClick = onSubmit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Send,
                    "Run",
                    tint = TermGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TermKeyButton(
    label: String,
    onClick: () -> Unit,
    color: Color = TermGray
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp).defaultMinSize(minWidth = 32.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
