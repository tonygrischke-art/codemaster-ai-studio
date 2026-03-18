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

private val TermBg    = Color(0xFF0D1117)
private val TermGreen = Color(0xFF3FB950)
private val TermRed   = Color(0xFFF85149)
private val TermYellow= Color(0xFFD29922)
private val TermBlue  = Color(0xFF58A6FF)
private val TermGray  = Color(0xFF8B949E)
private val TermWhite = Color(0xFFE6EDF3)

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
    LaunchedEffect(uiState.lines.size) {
        if (uiState.lines.isNotEmpty())
            scope.launch { listState.animateScrollToItem(uiState.lines.size - 1) }
    }

    Scaffold(
        modifier = Modifier.imePadding(),   // ← keyboard pushes content up
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Terminal", fontWeight = FontWeight.Bold, color = TermWhite)
                        Text(
                            uiState.cwd.takeLast(38),
                            style = MaterialTheme.typography.labelSmall,
                            color = TermGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = TermWhite) }
                },
                actions = {
                    if (uiState.isRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(TermGreen, RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(4.dp))
                            Text("LIVE", color = TermGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = { viewModel.submitCommand() }) {
                        Icon(Icons.Default.DeleteSweep, "Clear", tint = TermWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161B22))
            )
        },
        containerColor = TermBg,
        bottomBar = {
            Column(modifier = Modifier.background(Color(0xFF161B22))) {
                // Quick keys
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF21262D)).padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TermKey("Tab",  onClick = { viewModel.sendTab() })
                    TermKey("↑",    onClick = { viewModel.historyUp() })
                    TermKey("↓",    onClick = { viewModel.historyDown() })
                    TermKey("^C",   onClick = { viewModel.sendCtrlC() }, color = TermRed)
                    TermKey("^D",   onClick = { viewModel.sendCtrlD() }, color = TermYellow)
                    TermKey("~",    onClick = { viewModel.updateInput(uiState.input + "~") })
                    TermKey("/",    onClick = { viewModel.updateInput(uiState.input + "/") })
                    TermKey("-",    onClick = { viewModel.updateInput(uiState.input + "-") })
                    TermKey("|",    onClick = { viewModel.updateInput(uiState.input + "|") })
                    TermKey("&&",   onClick = { viewModel.updateInput(uiState.input + " && ") })
                }
                // Input row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$ ", color = TermGreen, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    TextField(
                        value = uiState.input,
                        onValueChange = { viewModel.updateInput(it) },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TermWhite
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = TermGreen
                        ),
                        placeholder = { Text("Enter command...", color = TermGray, fontFamily = FontFamily.Monospace, fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { viewModel.submitCommand() })
                    )
                    IconButton(onClick = { viewModel.submitCommand() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Send, "Run", tint = TermGreen, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).background(TermBg),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(uiState.lines) { line ->
                Text(
                    text = line.text,
                    color = when (line.type) {
                        LineType.INPUT  -> TermBlue
                        LineType.OUTPUT -> TermWhite
                        LineType.ERROR  -> TermRed
                        LineType.SYSTEM -> TermYellow
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun TermKey(label: String, onClick: () -> Unit, color: Color = Color(0xFF8B949E)) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp).defaultMinSize(minWidth = 32.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = color)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
