package com.codemaster.aistudio.terminal

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader

class EmbeddedTerminalManager(private val context: Context) {
    
    private var terminalContainer: ViewGroup? = null
    private var process: Process? = null
    private var outputWriter: PipedOutputStream? = null
    private var inputReader: BufferedReader? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private lateinit var scrollView: ScrollView
    private lateinit var outputText: TextView
    private lateinit var inputField: EditText
    
    companion object {
        private const val TAG = "EmbeddedTerminal"
    }

    suspend fun preInitialize() {
        withContext(Dispatchers.IO) {
            if (!isEnvironmentInstalled()) {
                installEnvironment()
            }
        }
    }

    fun createTerminalView(container: ViewGroup) {
        terminalContainer = container
        
        scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        outputText = TextView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            text = "CodeMaster Terminal v2.1\nType 'help' for commands\n\n"
        }

        inputField = EditText(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.parseColor("#2D2D2D"))
            setTextColor(Color.WHITE)
            hintTextColor = Color.GRAY
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            hint = "$ "
            setSingleLine(true)
            setOnEditorActionListener { _, _, _ ->
                val cmd = inputField.text.toString()
                if (cmd.isNotEmpty()) {
                    executeCommand(cmd)
                    inputField.setText("")
                }
                true
            }
        }

        scrollView.addView(outputText)
        container.addView(scrollView)
        container.addView(inputField)
        
        startShell()
    }

    private fun isEnvironmentInstalled(): Boolean {
        val prootBinary = File(context.filesDir, "proot/bin/proot")
        val rootfsDir = File(context.filesDir, "proot/rootfs/bin")
        return prootBinary.exists() && rootfsDir.exists()
    }

    private suspend fun installEnvironment() {
        withContext(Dispatchers.Main) {
            appendOutput("Installing terminal environment...\n")
        }

        try {
            val filesDir = context.filesDir
            val prootDir = File(filesDir, "proot")
            prootDir.mkdirs()

            extractAsset("bin/proot", File(prootDir, "bin/proot"))
            File(prootDir, "bin/proot").setExecutable(true)

            val rootfsDir = File(prootDir, "rootfs")
            rootfsDir.mkdirs()

            withContext(Dispatchers.Main) {
                appendOutput("Extracting Linux system...\n")
            }

            extractRootfs(rootfsDir)
            setupRootfsConfig(rootfsDir)

            withContext(Dispatchers.Main) {
                appendOutput("Installing packages...\n")
            }

            installPackages()

            withContext(Dispatchers.Main) {
                appendOutput("\nTerminal ready! Type 'help' for commands.\n\n")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            withContext(Dispatchers.Main) {
                appendOutput("Install failed: ${e.message}\n")
            }
        }
    }

    private fun extractRootfs(destDir: File) {
        try {
            context.assets.open("rootfs/alpine-rootfs.tar.gz").use { assetStream ->
                val tarFile = File(context.cacheDir, "rootfs.tar")
                FileOutputStream(tarFile).use { output ->
                    assetStream.copyTo(output)
                }
                
                Runtime.getRuntime().exec(arrayOf("tar", "-xf", tarFile.absolutePath, "-C", destDir.absolutePath)).waitFor()
                tarFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extract failed", e)
            throw e
        }
    }

    private fun setupRootfsConfig(rootfsDir: File) {
        File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        File(rootfsDir, "root/.profile").writeText("""
            export HOME=/root
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export LANG=C.UTF-8
            export TERM=xterm-256color
            alias ll='ls -la'
            alias cls='clear'
        """.trimIndent())
    }

    private suspend fun installPackages() {
        execInProot("apk update")
        execInProot("apk add --no-cache bash git curl wget openssh python3 py3-pip nodejs npm vim nano")
    }

    private fun startShell() {
        val proot = File(context.filesDir, "proot/bin/proot").absolutePath
        val rootfs = File(context.filesDir, "proot/rootfs").absolutePath

        val cmd = arrayOf(
            proot,
            "-r", rootfs,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-w", "/root",
            "/bin/sh"
        )

        try {
            process = ProcessBuilder(*cmd).apply {
                redirectErrorStream(true)
                environment()["TERM"] = "xterm-256color"
                environment()["HOME"] = "/root"
                environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
            }.start()

            inputReader = BufferedReader(InputStreamReader(process!!.inputStream))

            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                try {
                    var line: String?
                    while (inputReader?.readLine().also { line = it } != null) {
                        withContext(Dispatchers.Main) {
                            appendOutput(line + "\n")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Reader error", e)
                }
            }

            appendOutput("Shell started. Type 'help' to see available commands.\n\n")

        } catch (e: Exception) {
            Log.e(TAG, "Shell start failed", e)
            appendOutput("Failed to start shell: ${e.message}\n")
        }
    }

    fun executeCommand(command: String) {
        appendOutput("$ $command\n")
        
        when (command.trim()) {
            "clear" -> {
                outputText.text = ""
                return
            }
            "help" -> {
                appendOutput("""
                    Available commands:
                    - bash, git, curl, wget, python3, node, npm
                    - vim, nano for editing
                    - Standard Linux commands (ls, cd, cp, mv, rm, etc.)
                    
                    Quick commands:
                    - 'python3' - Start Python interpreter
                    - 'node' - Start Node.js REPL
                    - 'git status' - Check git status
                """.trimIndent() + "\n")
                return
            }
        }

        if (process != null && process!!.isAlive) {
            process!!.outputStream.write((command + "\n").toByteArray())
            process!!.outputStream.flush()
        } else {
            execInProot(command)
        }
    }

    private suspend fun execInProot(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val proot = File(context.filesDir, "proot/bin/proot").absolutePath
                val rootfs = File(context.filesDir, "proot/rootfs").absolutePath

                val process = ProcessBuilder(
                    proot, "-r", rootfs,
                    "-b", "/dev", "-b", "/proc", "-b", "/sys",
                    "-w", "/root",
                    "/bin/sh", "-c", command
                ).apply {
                    redirectErrorStream(true)
                    environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                }.start()

                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()
                
                withContext(Dispatchers.Main) {
                    appendOutput(output + "\n")
                }
                output
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendOutput("Error: ${e.message}\n")
                }
                ""
            }
        }
    }

    private fun appendOutput(text: String) {
        if (::outputText.isInitialized) {
            val currentText = outputText.text.toString()
            val lines = currentText.lines()
            val trimmed = if (lines.size > 500) lines.takeLast(500).joinToString("\n") else currentText
            outputText.text = trimmed + text
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun extractAsset(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Asset extract failed: $assetPath", e)
        }
    }
}
