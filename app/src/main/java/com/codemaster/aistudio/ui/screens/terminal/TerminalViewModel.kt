package com.codemaster.aistudio.ui.screens.terminal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

data class TerminalLine(
    val text: String,
    val type: LineType = LineType.OUTPUT
)

enum class LineType { INPUT, OUTPUT, ERROR, SYSTEM }

data class TerminalUiState(
    val lines: List<TerminalLine> = emptyList(),
    val input: String = "",
    val isRunning: Boolean = false,
    val cwd: String = "",
    val shell: String = "",
    val history: List<String> = emptyList(),
    val historyIndex: Int = -1
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var readerJob: Job? = null
    private val maxLines = 2000

    // Detect the best available shell
    private val shellPath: String by lazy {
        listOf(
            "/data/data/com.termux/files/usr/bin/bash",   // Termux bash (best)
            "/data/data/com.termux/files/usr/bin/sh",     // Termux sh
            "/system/bin/sh",                              // Android sh (fallback)
            "/system/xbin/sh"                              // Some ROMs
        ).firstOrNull { File(it).exists() } ?: "/system/bin/sh"
    }

    private val isTermuxAvailable: Boolean
        get() = File("/data/data/com.termux/files/usr/bin/bash").exists()

    fun init(projectId: Long) {
        val homeDir = if (isTermuxAvailable) {
            "/data/data/com.termux/files/home"
        } else {
            context.filesDir.absolutePath
        }

        appendLine(TerminalLine("╔════════════════════════════════════════╗", LineType.SYSTEM))
        appendLine(TerminalLine("║     CodeMaster AI Studio Terminal      ║", LineType.SYSTEM))
        appendLine(TerminalLine("╚════════════════════════════════════════╝", LineType.SYSTEM))
        appendLine(TerminalLine("Shell: $shellPath", LineType.SYSTEM))
        appendLine(TerminalLine(if (isTermuxAvailable) "✅ Termux environment detected" else "⚠️ Using Android system shell", LineType.SYSTEM))
        appendLine(TerminalLine("Type 'help' for built-in commands", LineType.SYSTEM))
        appendLine(TerminalLine("", LineType.SYSTEM))

        startShell(homeDir)
    }

    private fun startShell(startDir: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(shellPath, "--login").apply {
                    directory(File(startDir).takeIf { it.exists() } ?: context.filesDir)
                    environment().apply {
                        if (isTermuxAvailable) {
                            put("HOME", "/data/data/com.termux/files/home")
                            put("PATH", "/data/data/com.termux/files/usr/bin:/data/data/com.termux/files/usr/bin/applets:/system/bin:/system/xbin")
                            put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib")
                            put("PREFIX", "/data/data/com.termux/files/usr")
                            put("TMPDIR", "/data/data/com.termux/files/usr/tmp")
                        } else {
                            put("PATH", "/system/bin:/system/xbin")
                            put("HOME", context.filesDir.absolutePath)
                        }
                        put("TERM", "xterm-256color")
                        put("LANG", "en_US.UTF-8")
                        put("COLORTERM", "truecolor")
                    }
                    redirectErrorStream(false)
                }

                process = pb.start()
                writer = OutputStreamWriter(process!!.outputStream, Charsets.UTF_8)

                _uiState.value = _uiState.value.copy(
                    isRunning = true,
                    shell = shellPath,
                    cwd = startDir
                )

                // Read stdout
                readerJob = viewModelScope.launch(Dispatchers.IO) {
                    val stdout = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
                    val stderr = BufferedReader(InputStreamReader(process!!.errorStream, Charsets.UTF_8))

                    launch {
                        try {
                            var line: String?
                            while (isActive) {
                                line = stdout.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.OUTPUT))
                            }
                        } catch (_: Exception) {}
                    }

                    launch {
                        try {
                            var line: String?
                            while (isActive) {
                                line = stderr.readLine() ?: break
                                appendLine(TerminalLine(stripAnsi(line), LineType.ERROR))
                            }
                        } catch (_: Exception) {}
                    }
                }

            } catch (e: Exception) {
                appendLine(TerminalLine("Failed to start shell: ${e.message}", LineType.ERROR))
                appendLine(TerminalLine("Falling back to built-in command handler", LineType.SYSTEM))
                _uiState.value = _uiState.value.copy(isRunning = false)
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(input = text)
    }

    fun submitCommand() {
        val cmd = _uiState.value.input.trim()
        if (cmd.isEmpty()) return

        appendLine(TerminalLine("$ $cmd", LineType.INPUT))

        val newHistory = (_uiState.value.history + cmd).takeLast(100)
        _uiState.value = _uiState.value.copy(
            input = "",
            history = newHistory,
            historyIndex = -1
        )

        // Handle built-in commands first
        when {
            cmd == "clear" || cmd == "cls" -> {
                _uiState.value = _uiState.value.copy(lines = emptyList())
                return
            }
            cmd == "help" -> {
                showHelp()
                return
            }
            cmd == "exit" -> {
                appendLine(TerminalLine("Type 'clear' to reset, or use device back button", LineType.SYSTEM))
                return
            }
        }

        if (_uiState.value.isRunning && writer != null) {
            // Send to real shell process
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    writer?.write("$cmd
")
                    writer?.flush()
                } catch (e: Exception) {
                    appendLine(TerminalLine("Error: ${e.message}", LineType.ERROR))
                }
            }
        } else {
            // Fallback built-in executor
            runBuiltinCommand(cmd)
        }
    }

    fun historyUp() {
        val history = _uiState.value.history
        if (history.isEmpty()) return
        val newIndex = (_uiState.value.historyIndex + 1).coerceAtMost(history.size - 1)
        val cmd = history[history.size - 1 - newIndex]
        _uiState.value = _uiState.value.copy(input = cmd, historyIndex = newIndex)
    }

    fun historyDown() {
        val newIndex = _uiState.value.historyIndex - 1
        if (newIndex < 0) {
            _uiState.value = _uiState.value.copy(input = "", historyIndex = -1)
        } else {
            val history = _uiState.value.history
            val cmd = history[history.size - 1 - newIndex]
            _uiState.value = _uiState.value.copy(input = cmd, historyIndex = newIndex)
        }
    }

    fun sendCtrlC() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.write("")
                writer?.flush()
            } catch (_: Exception) {}
        }
        appendLine(TerminalLine("^C", LineType.SYSTEM))
    }

    fun sendCtrlD() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.write("")
                writer?.flush()
            } catch (_: Exception) {}
        }
    }

    fun sendTab() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.write("	")
                writer?.flush()
            } catch (_: Exception) {}
        }
    }

    private fun runBuiltinCommand(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parts = cmd.trim().split("\s+".toRegex())
                val result = when (parts[0]) {
                    "echo" -> parts.drop(1).joinToString(" ")
                    "pwd" -> _uiState.value.cwd
                    "ls" -> {
                        val dir = File(if (parts.size > 1) parts[1] else _uiState.value.cwd)
                        dir.listFiles()?.joinToString("  ") { f ->
                            if (f.isDirectory) "${f.name}/" else f.name
                        } ?: "Cannot list directory"
                    }
                    "cat" -> {
                        if (parts.size < 2) "Usage: cat <file>"
                        else {
                            val f = File(_uiState.value.cwd, parts[1])
                            if (f.exists()) f.readText() else "No such file: ${parts[1]}"
                        }
                    }
                    "cd" -> {
                        val target = if (parts.size < 2 || parts[1] == "~") {
                            if (isTermuxAvailable) "/data/data/com.termux/files/home" else context.filesDir.absolutePath
                        } else if (parts[1].startsWith("/")) parts[1]
                        else "${_uiState.value.cwd}/${parts[1]}"
                        val dir = File(target)
                        if (dir.exists() && dir.isDirectory) {
                            _uiState.value = _uiState.value.copy(cwd = dir.canonicalPath)
                            ""
                        } else "cd: $target: No such directory"
                    }
                    "mkdir" -> {
                        if (parts.size < 2) "Usage: mkdir <dir>"
                        else {
                            File(_uiState.value.cwd, parts[1]).mkdirs()
                            ""
                        }
                    }
                    "rm" -> {
                        if (parts.size < 2) "Usage: rm <file>"
                        else {
                            File(_uiState.value.cwd, parts[1]).delete()
                            ""
                        }
                    }
                    "uname" -> "Linux (Android)"
                    "whoami" -> "codemaster"
                    "date" -> java.util.Date().toString()
                    "env" -> System.getenv().entries.joinToString("
") { "${it.key}=${it.value}" }
                    else -> {
                        // Try running as system command
                        val proc = Runtime.getRuntime().exec(cmd)
                        val out = proc.inputStream.bufferedReader().readText()
                        val err = proc.errorStream.bufferedReader().readText()
                        proc.waitFor()
                        if (err.isNotBlank()) {
                            withContext(Dispatchers.Main) { appendLine(TerminalLine(err.trim(), LineType.ERROR)) }
                        }
                        out.trim()
                    }
                }
                if (result.isNotBlank()) {
                    result.lines().forEach { line ->
                        appendLine(TerminalLine(line, LineType.OUTPUT))
                    }
                }
            } catch (e: Exception) {
                appendLine(TerminalLine("${parts.firstOrNull() ?: cmd}: ${e.message ?: "command failed"}", LineType.ERROR))
            }
        }
    }

    private fun showHelp() {
        val helpLines = listOf(
            "━━━ Built-in Commands ━━━━━━━━━━━━━━━━━━━",
            "  clear / cls   Clear terminal",
            "  cd <dir>      Change directory",
            "  ls [dir]      List files",
            "  cat <file>    Print file contents",
            "  mkdir <dir>   Create directory",
            "  rm <file>     Delete file",
            "  pwd           Print working directory",
            "  echo <text>   Print text",
            "  env           Show environment",
            "  uname         System info",
            "  whoami        Current user",
            "  date          Current date/time",
            "  help          This help",
            "",
            if (isTermuxAvailable)
                "✅ Full Termux shell active — all pkg commands available"
            else
                "⚠️ No Termux detected — install Termux for full shell access",
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        )
        helpLines.forEach { appendLine(TerminalLine(it, LineType.SYSTEM)) }
    }

    private fun appendLine(line: TerminalLine) {
        val current = _uiState.value.lines.toMutableList()
        current.add(line)
        if (current.size > maxLines) current.removeAt(0)
        _uiState.value = _uiState.value.copy(lines = current)
    }

    // Strip basic ANSI escape codes for cleaner display
    private fun stripAnsi(text: String): String =
        text.replace(Regex("\[[;\d]*[A-Za-z]"), "")
            .replace(Regex("\][^]*"), "")

    override fun onCleared() {
        super.onCleared()
        readerJob?.cancel()
        try {
            writer?.close()
            process?.destroy()
        } catch (_: Exception) {}
    }
}
