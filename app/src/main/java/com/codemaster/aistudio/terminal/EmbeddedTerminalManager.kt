package com.codemaster.aistudio.terminal

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EmbeddedTerminalManager(private val context: Context) {
    
    private var terminalView: TerminalView? = null
    private var terminalSession: TerminalSession? = null
    private val handler = Handler(Looper.getMainLooper())
    
    suspend fun preInitialize() {
        withContext(Dispatchers.IO) {
            if (!isEnvironmentInstalled()) {
                installEnvironment()
            }
        }
    }

    fun createTerminalView(container: ViewGroup): TerminalView {
        terminalView = TerminalView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        
        container.addView(terminalView)
        
        GlobalScope.launch(Dispatchers.IO) {
            if (!isEnvironmentInstalled()) {
                installEnvironment()
            }
            startSession()
        }
        
        return terminalView!!
    }

    private fun isEnvironmentInstalled(): Boolean {
        val prootBinary = File(context.filesDir, "proot/bin/proot")
        val rootfsDir = File(context.filesDir, "proot/rootfs/bin")
        return prootBinary.exists() && rootfsDir.exists()
    }

    private suspend fun installEnvironment() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Installing terminal...", Toast.LENGTH_LONG).show()
        }

        try {
            val filesDir = context.filesDir
            val prootDir = File(filesDir, "proot")
            prootDir.mkdirs()

            extractAsset("bin/proot", File(prootDir, "bin/proot"))
            File(prootDir, "bin/proot").setExecutable(true)
            extractAsset("bin/proot-loader", File(prootDir, "bin/proot-loader"))
            File(prootDir, "bin/proot-loader").setExecutable(true)

            val rootfsDir = File(prootDir, "rootfs")
            rootfsDir.mkdirs()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Extracting Linux system...", Toast.LENGTH_SHORT).show()
            }

            extractRootfs(File(filesDir, "rootfs/alpine-rootfs.tar.gz"), rootfsDir)
            setupRootfsConfig(rootfsDir)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Installing packages...", Toast.LENGTH_SHORT).show()
            }

            installPackages()

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Terminal ready!", Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
            Log.e("EmbeddedTerminal", "Install failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startSession() {
        val proot = File(context.filesDir, "proot/bin/proot").absolutePath
        val rootfs = File(context.filesDir, "proot/rootfs").absolutePath

        val args = arrayOf(
            proot, "-r", rootfs, "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", "/sdcard:/mnt/sdcard", "-w", "/root", "/bin/bash", "--login"
        )

        val env = arrayOf(
            "TERM=xterm-256color", "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "LANG=C.UTF-8", "PWD=/root"
        )

        terminalSession = TerminalSession(
            "/system/bin/sh", context.filesDir.absolutePath, args, env,
            TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            object : TerminalSession.SessionChangedCallback {
                override fun onTitleChanged(title: String?) {}
                override fun onSessionFinished(finishedSession: TerminalSession?) {
                    handler.postDelayed({ startSession() }, 1000)
                }
                override fun onClipboardText(text: String?) {
                    text?.let {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", it))
                    }
                }
                override fun onBell() {}
                override fun onColorsChanged(changedSession: TerminalSession?) {}
            }
        )

        handler.post {
            terminalView?.attachSession(terminalSession)
            terminalSession?.initializeEmulator(80, 24)
        }
    }

    fun executeCommand(command: String) {
        terminalSession?.write("$command\r")
    }

    private suspend fun extractRootfs(archive: File, destDir: File) {
        withContext(Dispatchers.IO) {
            val process = Runtime.getRuntime().exec(arrayOf(
                "tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath
            ))
            process.waitFor()
        }
    }

    private fun setupRootfsConfig(rootfsDir: File) {
        File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        File(rootfsDir, "root/.profile").writeText("""
            export HOME=/root
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export LANG=C.UTF-8
            alias ll='ls -la'
            alias cls='clear'
            echo "CodeMaster Terminal Ready"
        """.trimIndent())
    }

    private suspend fun installPackages() {
        execInProot("apk update")
        execInProot("apk add --no-cache bash git curl openssh-client python3 py3-pip nodejs npm vim nano")
        execInProot("pip3 install --upgrade pip setuptools wheel")
    }

    private suspend fun execInProot(command: String): String {
        return withContext(Dispatchers.IO) {
            val proot = File(context.filesDir, "proot/bin/proot").absolutePath
            val rootfs = File(context.filesDir, "proot/rootfs").absolutePath
            val process = ProcessBuilder(
                proot, "-r", rootfs, "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-w", "/root", "/bin/sh", "-c", command
            ).apply {
                environment()["PATH"] = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                redirectErrorStream(true)
            }.start()
            process.inputStream.bufferedReader().readText().also { process.waitFor() }
        }
    }

    private fun extractAsset(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) }
        }
    }
}
