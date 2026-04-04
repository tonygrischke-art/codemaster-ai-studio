package com.codemaster.aistudio.data.terminal

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages a self-contained Alpine Linux + proot environment stored in
 * the app's private files directory. No Termux, no root required.
 *
 * Layout after setup:
 *   filesDir/embedded/
 *     proot            <- static proot binary (ARM64)
 *     rootfs/          <- Alpine Linux 3.19 minirootfs
 *       bin/sh
 *       etc/resolv.conf
 *       root/.profile
 *       ...
 */
@Singleton
class EmbeddedEnvironment @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG         = "EmbeddedEnv"
        private const val MARKER_FILE = ".setup_complete_v1"

        // proot static binary — ARM64, zero dependencies
        private const val PROOT_URL =
            "https://skirsten.github.io/proot-portable-android-binaries/aarch64/proot"

        // Alpine Linux 3.19 minirootfs for aarch64 (~8 MB)
        private const val ALPINE_URL =
            "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"
    }

    val embeddedDir: File get() = File(context.filesDir, "embedded")
    val rootfsDir:   File get() = File(embeddedDir, "rootfs")
    val prootBin:    File get() = File(embeddedDir, "proot")
    private val markerFile: File get() = File(embeddedDir, MARKER_FILE)

    /** True once the full setup has completed successfully. */
    val isReady: Boolean
        get() = markerFile.exists()
                && prootBin.exists()
                && prootBin.canExecute()
                && File(rootfsDir, "bin/sh").exists()

    sealed class SetupState {
        object NotStarted  : SetupState()
        object Checking    : SetupState()
        data class Downloading(val label: String, val progress: Int) : SetupState()
        data class Extracting(val label: String) : SetupState()
        object Configuring : SetupState()
        object Ready       : SetupState()
        data class Failed(val reason: String) : SetupState()
    }

    private val _state = MutableStateFlow<SetupState>(SetupState.NotStarted)
    val state: StateFlow<SetupState> = _state.asStateFlow()

    /** Shell command list that launches the embedded Alpine shell. */
    fun shellCommand(startDir: String = "/root"): List<String> = listOf(
        prootBin.absolutePath,
        "--rootfs=${rootfsDir.absolutePath}",
        "--change-id=0:0",
        "-w", startDir,
        "-b", "/proc",
        "-b", "/dev",
        "-b", "/sys",
        "/bin/sh", "-l"
    )

    /** Environment variables to inject into the proot shell process. */
    fun shellEnv(): Map<String, String> = mapOf(
        "HOME"  to "/root",
        "TERM"  to "xterm-256color",
        "SHELL" to "/bin/sh",
        "PATH"  to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "LANG"  to "en_US.UTF-8",
        "USER"  to "root",
        "PROOT_TMP_DIR" to context.cacheDir.absolutePath
    )

    // ── Main entry point ──────────────────────────────────────────────────

    suspend fun setup(onProgress: (String, Int) -> Unit = { _, _ -> }) {
        if (isReady) { _state.value = SetupState.Ready; return }

        withContext(Dispatchers.IO) {
            try {
                _state.value = SetupState.Checking
                onProgress("Preparing directories...", 0)
                embeddedDir.mkdirs()
                rootfsDir.mkdirs()

                // Step 1 — proot binary (~1.5 MB)
                if (!prootBin.exists() || !prootBin.canExecute()) {
                    _state.value = SetupState.Downloading("Downloading proot binary...", 5)
                    onProgress("Downloading proot binary...", 5)
                    downloadFile(PROOT_URL, prootBin) { p ->
                        val pct = 5 + (p * 0.20).toInt()
                        _state.value = SetupState.Downloading("Downloading proot... $p%", pct)
                        onProgress("Downloading proot... $p%", pct)
                    }
                    prootBin.setExecutable(true, false)
                    Log.i(TAG, "proot ready: ${prootBin.absolutePath}")
                }

                // Step 2 — Alpine rootfs tarball (~8 MB)
                val tarGz = File(embeddedDir, "alpine.tar.gz")
                if (!File(rootfsDir, "bin/sh").exists()) {
                    if (!tarGz.exists() || tarGz.length() < 1_000_000L) {
                        _state.value = SetupState.Downloading("Downloading Alpine Linux (~8 MB)...", 25)
                        onProgress("Downloading Alpine Linux (~8 MB)...", 25)
                        downloadFile(ALPINE_URL, tarGz) { p ->
                            val pct = 25 + (p * 0.45).toInt()
                            _state.value = SetupState.Downloading("Downloading Alpine... $p%", pct)
                            onProgress("Downloading Alpine... $p%", pct)
                        }
                    }

                    // Step 3 — extract
                    _state.value = SetupState.Extracting("Extracting Alpine Linux...")
                    onProgress("Extracting Alpine Linux...", 70)
                    extractTar(tarGz, rootfsDir)
                    tarGz.delete()
                    Log.i(TAG, "rootfs extracted to ${rootfsDir.absolutePath}")
                }

                // Step 4 — configure
                _state.value = SetupState.Configuring
                onProgress("Configuring environment...", 90)
                configureRootfs()

                // Done
                markerFile.writeText("ok")
                _state.value = SetupState.Ready
                onProgress("Terminal ready!", 100)
                Log.i(TAG, "Embedded environment setup complete")

            } catch (e: Exception) {
                Log.e(TAG, "Setup failed", e)
                val msg = e.message?.let { "Setup failed: $it" } ?: "Setup failed: Check internet connection and try again"
                _state.value = SetupState.Failed(msg)
                onProgress(msg, -1)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun downloadFile(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        // Try up to 3 times with exponential backoff
        var lastException: Exception? = null
        for (attempt in 1..3) {
            try {
                downloadFileOnce(urlStr, dest, onProgress)
                return
            } catch (e: Exception) {
                lastException = e
                if (attempt < 3) Thread.sleep(2000L * attempt)
            }
        }
        throw lastException ?: RuntimeException("Download failed after 3 attempts")
    }

    private fun downloadFileOnce(urlStr: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 60_000
            readTimeout    = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CodeMaster-AI-Studio/2.0")
            connect()
        }
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("HTTP ${conn.responseCode} from $urlStr")
        }
        val total = conn.contentLengthLong
        var downloaded = 0L
        BufferedInputStream(conn.inputStream).use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                }
            }
        }
    }

    private fun extractTar(tarGz: File, destDir: File) {
        val proc = ProcessBuilder("tar", "xzf", tarGz.absolutePath, "-C", destDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val out  = proc.inputStream.bufferedReader().readText()
        val exit = proc.waitFor()
        if (exit != 0) throw RuntimeException("tar failed (exit $exit): $out")
    }

    private fun configureRootfs() {
        // DNS
        File(rootfsDir, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        // Hostname
        File(rootfsDir, "etc/hostname").writeText("codemaster\n")
        // Hosts
        File(rootfsDir, "etc/hosts").writeText(
            "127.0.0.1   localhost codemaster\n::1         localhost\n"
        )
        // MOTD
        File(rootfsDir, "etc/motd").writeText(
            "\n╔═══════════════════════════════╗\n" +
            "║  CodeMaster Built-in Terminal  ║\n" +
            "║  Alpine Linux 3.19 (ARM64)     ║\n" +
            "║  apk add python3 git nodejs    ║\n" +
            "╚═══════════════════════════════╝\n"
        )
        // Shell profile
        File(rootfsDir, "root/.profile").apply {
            parentFile?.mkdirs()
            writeText(
                "export PS1='\\[\\033[1;32m\\]codemaster\\[\\033[0m\\]:\\[\\033[1;34m\\]\\w\\[\\033[0m\\]# '\n" +
                "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin\n" +
                "cat /etc/motd\n"
            )
        }
    }

    /** Wipe everything — setup will run again on next terminal open. */
    fun reset() {
        embeddedDir.deleteRecursively()
        _state.value = SetupState.NotStarted
    }
}