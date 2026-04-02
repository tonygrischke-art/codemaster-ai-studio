package com.codemaster.aistudio.ui.screens.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.util.PlatformHelper
import com.codemaster.aistudio.data.terminal.EmbeddedEnvironment
import com.codemaster.aistudio.data.util.SafHelper
import com.codemaster.aistudio.data.terminal.EmbeddedEnvironment
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

data class TerminalLine(val text: String, val type: LineType = LineType.OUTPUT)
enum class LineType { INPUT, OUTPUT, ERROR, SYSTEM }

data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val input: String = "",
    val isRunning: Boolean = false,
    val cwd: String = "",
    val shell: String = "",
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val isTermuxAvailable: Boolean = false,
    val isSafMode: Boolean = false
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformHelper: PlatformHelper,
    private val safHelper: SafHelper,
    private val embeddedEnv: EmbeddedEnvironment
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var readerJob: Job? = null
    private val maxLines = 2000

    private fun buildShellCommand(): List<String> {
        return embeddedEnv.shellCommand()
    }

    fun init(projectId: Long) {
        val isTermux = platformHelper.isTermuxAvailable
        val isSaf = platformHelper.isUsingSaf
        val homeDir = platformHelper.homeDirectory
        val shellCmd = buildShellCommand()

        appendLine(TerminalLine("=== CodeMaster AI Studio Terminal ===", LineType.SYSTEM))
        appendLine(TerminalLine("Shell: ${shellCmd.first()}", LineType.SYSTEM))
        
        when {
            isTermux -> {
                appendLine(TerminalLine("Termux environment detected", LineType.SYSTEM))
                appendLine(TerminalLine("Git available: ${platformHelper.isTermuxGitAvailable()}", LineType.SYSTEM))
            }
            isSaf -> {
                appendLine(TerminalLine("Storage Access Framework mode", LineType.SYSTEM))
                appendLine(TerminalLine("Note: Shell commands limited. Use Termux for full terminal.", LineType.SYSTEM))
            }
            else -> {
                appendLine(TerminalLine("Android system shell (limited)", LineType.SYSTEM))
                appendLine(TerminalLine("Install Termux for full shell + git support", LineType.SYSTEM))
            }
        }
        
        appendLine(TerminalLine("Type 'help' for commands", LineType.SYSTEM))
        appendLine(TerminalLine("", LineType.SYSTEM))
        
        _uiState.value = _uiState.value.copy(
            isTermuxAvailable = isTermux,
            isSafMode = isSaf,
            cwd = homeDir
        )
        
        startShell(homeDir, shellCmd)
    }

    private fun startShell(startDir: String, shellCmd: List<String>) {
        if (startDir.startsWith("saf://")) {
            appendLine(TerminalLine("Cannot start shell in SAF directory", LineType.ERROR))
            appendLine(TerminalLine("Working directory: ${startDir}", LineType.SYSTEM))
            _uiState.value = _uiState.value.copy(isRunning = false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val workDir = File(startDir).takeIf { it.exists() && it.isDirectory } ?: context.filesDir

                val pb = ProcessBuilder(shellCmd).apply {
                    directory(workDir)
                    environment().apply {
                        if (embeddedEnv.isReady) {
                            embeddedEnv.shellEnv().forEach { (k, v) -> put(k, v) }
                        } else {
                            platformHelper.buildEnvironment().forEach { (k, v) -> put(k, v) }
                            put("LANG", "en_US.UTF-8")
                        }
                    }
                    redirectErrorStream(false)
                }

                val proc = pb.start()
                process = proc
                writer = OutputStreamWriter(proc.outputStream, Charsets.UTF_8)
                _uiState.value = _uiState.value.copy(
                    isRunning = true,
                    shell = shellCmd.first(),
                    cwd = startDir
                )

                readerJob = viewModelScope.launch(Dispatchers.IO) {
                    launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.OUTPUT))
                            }
                        } catch (e: Exception) {
                            appendLine(TerminalLine("Output reader error: ${e.message}", LineType.ERROR))
                        }
                    }
                    launch {
                        try {
                            val reader = BufferedReader(InputStreamReader(proc.errorStream, Charsets.UTF_8))
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.ERROR))
                            }
                        } catch (e: Exception) {
                            appendLine(TerminalLine("Error reader error: ${e.message}", LineType.ERROR))
                        }
                    }
                }
            } catch (e: Exception) {
                appendLine(TerminalLine("Shell failed: ${e.message}", LineType.ERROR))
                appendLine(TerminalLine("Using built-in commands only", LineType.SYSTEM))
                _uiState.value = _uiState.value.copy(isRunning = false, cwd = context.filesDir.absolutePath)
            }
        }
    }

    fun updateInput(text: String) { _uiState.value = _uiState.value.copy(input = text) }

    fun submitCommand() {
        val cmd = _uiState.value.input.trim()
        if (cmd.isEmpty()) return
        appendLine(TerminalLine("$ $cmd", LineType.INPUT))
        val newHistory = (_uiState.value.history + cmd).takeLast(100)
        _uiState.value = _uiState.value.copy(input = "", history = newHistory, historyIndex = -1)
        
        when (cmd) {
            "clear", "cls" -> { _uiState.value = _uiState.value.copy(lines = emptyList()); return }
            "help"         -> { showHelp(); return }
            "exit"         -> { appendLine(TerminalLine("Use back button to exit", LineType.SYSTEM)); return }
            "git"          -> { showGitHelp(); return }
        }
        
        if (_uiState.value.isRunning && writer != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try { 
                    writer?.write(cmd + "\n"); 
                    writer?.flush() 
                } catch (e: Exception) { 
                    appendLine(TerminalLine("Write error: ${e.message}", LineType.ERROR)) 
                }
            }
        } else {
            runBuiltinCommand(cmd)
        }
    }

    fun historyUp() {
        val h = _uiState.value.history
        if (h.isEmpty()) return
        val i = (_uiState.value.historyIndex + 1).coerceAtMost(h.size - 1)
        _uiState.value = _uiState.value.copy(input = h[h.size - 1 - i], historyIndex = i)
    }

    fun historyDown() {
        val i = _uiState.value.historyIndex - 1
        if (i < 0) _uiState.value = _uiState.value.copy(input = "", historyIndex = -1)
        else {
            val h = _uiState.value.history
            _uiState.value = _uiState.value.copy(input = h[h.size - 1 - i], historyIndex = i)
        }
    }

    fun sendCtrlC() {
        viewModelScope.launch(Dispatchers.IO) {
            try { writer?.write("\u0003"); writer?.flush() } catch (e: Exception) {
                appendLine(TerminalLine("Ctrl+C failed: ${e.message}", LineType.ERROR))
            }
        }
        appendLine(TerminalLine("^C", LineType.SYSTEM))
    }

    fun sendCtrlD() {
        viewModelScope.launch(Dispatchers.IO) {
            try { writer?.write("\u0004"); writer?.flush() } catch (e: Exception) {
                appendLine(TerminalLine("Ctrl+D failed: ${e.message}", LineType.ERROR))
            }
        }
    }

    fun sendTab() {
        viewModelScope.launch(Dispatchers.IO) {
            try { writer?.write("\t"); writer?.flush() } catch (e: Exception) {
                appendLine(TerminalLine("Tab completion failed: ${e.message}", LineType.ERROR))
            }
        }
    }

    private fun runBuiltinCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parts = cmd.trim().split("\\s+".toRegex())
                val result = when (parts[0]) {
                    "echo"   -> parts.drop(1).joinToString(" ")
                    "pwd"    -> _uiState.value.cwd
                    "whoami" -> "codemaster"
                    "uname"  -> "Linux (Android)"
                    "date"   -> java.util.Date().toString()
                    "ls" -> {
                        val dir = File(if (parts.size > 1) parts[1] else _uiState.value.cwd)
                        if (_uiState.value.isSafMode) {
                            "ls not available in SAF mode. Use app file browser."
                        } else {
                            dir.listFiles()?.joinToString("  ") { f ->
                                if (f.isDirectory) f.name + "/" else f.name
                            } ?: "Cannot list directory"
                        }
                    }
                    "cat" -> {
                        if (parts.size < 2) "Usage: cat <file>"
                        else {
                            if (_uiState.value.isSafMode) {
                                "cat not available in SAF mode. Use app file editor."
                            } else {
                                val f = File(_uiState.value.cwd, parts[1])
                                if (f.exists()) f.readText() else "No such file: " + parts[1]
                            }
                        }
                    }
                    "cd" -> {
                        val target = when {
                            parts.size < 2 || parts[1] == "~" -> platformHelper.homeDirectory
                            parts[1].startsWith("/") -> parts[1]
                            else -> _uiState.value.cwd + "/" + parts[1]
                        }
                        if (_uiState.value.isSafMode && target.startsWith("saf://")) {
                            "cd not available in SAF mode"
                        } else {
                            val dir = File(target)
                            if (dir.exists() && dir.isDirectory) {
                                _uiState.value = _uiState.value.copy(cwd = dir.canonicalPath); ""
                            } else "cd: $target: No such directory"
                        }
                    }
                    "mkdir" -> { 
                        if (parts.size < 2) "Usage: mkdir <dir>" 
                        else if (_uiState.value.isSafMode) "mkdir not available in SAF mode"
                        else { File(_uiState.value.cwd, parts[1]).mkdirs(); "" } 
                    }
                    "rm"    -> { 
                        if (parts.size < 2) "Usage: rm <file>"  
                        else if (_uiState.value.isSafMode) "rm not available in SAF mode"
                        else { File(_uiState.value.cwd, parts[1]).delete();  "" } 
                    }
                    "env"   -> System.getenv().entries.joinToString("\n") { it.key + "=" + it.value }
                    else -> {
                        if (_uiState.value.isSafMode) {
                            "Command not available in SAF mode. Install Termux for full shell."
                        } else {
                            val proc = Runtime.getRuntime().exec(cmd)
                            val out = proc.inputStream.bufferedReader().readText()
                            val err = proc.errorStream.bufferedReader().readText()
                            proc.waitFor()
                            if (err.isNotBlank()) appendLine(TerminalLine(err.trim(), LineType.ERROR))
                            out.trim()
                        }
                    }
                }
                if (result.isNotBlank()) result.lines().forEach { appendLine(TerminalLine(it, LineType.OUTPUT)) }
            } catch (e: Exception) {
                appendLine(TerminalLine(e.message ?: "command failed", LineType.ERROR))
            }
        }
    }

    private fun showHelp() {
        appendLine(TerminalLine("Built-in: clear, cd, pwd, echo, env, uname, whoami, date, help", LineType.SYSTEM))
        appendLine(TerminalLine("File ops: ls, cat, mkdir, rm (only in app storage or Termux)", LineType.SYSTEM))
        when {
            _uiState.value.isTermuxAvailable -> {
                appendLine(TerminalLine("Termux active - available: pkg, git, python, node, etc.", LineType.SYSTEM))
            }
            _uiState.value.isSafMode -> {
                appendLine(TerminalLine("SAF mode - use app's file browser/editor instead", LineType.SYSTEM))
            }
            else -> {
                appendLine(TerminalLine("Install Termux from Play Store/F-Droid for full shell", LineType.SYSTEM))
            }
        }
    }

    private fun showGitHelp() {
        if (platformHelper.isTermuxGitAvailable()) {
            appendLine(TerminalLine("Git is available in Termux", LineType.SYSTEM))
            appendLine(TerminalLine("Usage: git <command> [options]", LineType.SYSTEM))
        } else {
            appendLine(TerminalLine("Git not available", LineType.ERROR))
            appendLine(TerminalLine(platformHelper.getGitInstallInstructions(), LineType.SYSTEM))
        }
    }

    private fun appendLine(line: TerminalLine) {
        val current = _uiState.value.lines.toMutableList()
        current.add(line)
        if (current.size > maxLines) current.removeAt(0)
        _uiState.value = _uiState.value.copy(lines = current)
    }

    private fun stripAnsi(text: String): String =
        text.replace(Regex("\u001B\\[[;\\d]*[A-Za-z]"), "")
            .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")

    override fun onCleared() {
        super.onCleared()
        readerJob?.cancel()
        try {
            writer?.close()
            process?.destroyForcibly()
        } catch (e: Exception) {
        }
    }
}