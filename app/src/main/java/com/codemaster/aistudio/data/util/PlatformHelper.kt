package com.codemaster.aistudio.data.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TERMUX_PREFIX = "/data/data/com.termux/files"
        private const val TERMUX_HOME = "$TERMUX_PREFIX/home"
        private const val TERMUX_USR_BIN = "$TERMUX_PREFIX/usr/bin"
        private const val TERMUX_USR_LIB = "$TERMUX_PREFIX/usr/lib"
    }

    val isTermuxAvailable: Boolean
        get() = File("$TERMUX_USR_BIN/bash").exists()

    val isTermuxBootstrapInstalled: Boolean
        get() = File("$TERMUX_PREFIX").exists() && File("$TERMUX_PREFIX/usr").exists()

    val homeDirectory: String
        get() = if (isTermuxAvailable) TERMUX_HOME else context.filesDir.absolutePath

    val documentsDirectory: String
        get() = if (isTermuxAvailable) {
            "$TERMUX_HOME/Documents"
        } else {
            context.getExternalFilesDir(null)?.absolutePath ?: "${context.filesDir}/Documents"
        }

    val gitBinary: String?
        get() = listOf(
            "$TERMUX_USR_BIN/git",
            "/system/bin/git",
            "/system/xbin/git"
        ).firstOrNull { File(it).exists() }

    val bashBinary: String?
        get() = listOf(
            "$TERMUX_USR_BIN/bash",
            "$TERMUX_USR_BIN/sh",
            "/system/bin/sh",
            "/system/xbin/sh"
        ).firstOrNull { File(it).exists() }

    fun buildEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        if (isTermuxAvailable) {
            env["HOME"] = TERMUX_HOME
            env["PATH"] = "$TERMUX_USR_BIN:/system/bin:/system/xbin"
            env["LD_LIBRARY_PATH"] = TERMUX_USR_LIB
            env["GIT_EXEC_PATH"] = "$TERMUX_PREFIX/libexec/git-core"
            env["GIT_TEMPLATE_DIR"] = "$TERMUX_PREFIX/share/git-core/templates"
            env["PREFIX"] = TERMUX_PREFIX
            env["TMPDIR"] = "$TERMUX_PREFIX/tmp"
        } else {
            env["HOME"] = context.filesDir.absolutePath
            env["PATH"] = "/system/bin:/system/xbin"
        }
        env["TERM"] = "xterm-256color"
        return env
    }

    fun createProjectDirectory(projectName: String): String {
        val baseDir = documentsDirectory
        val projectDir = File(baseDir, projectName)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir.absolutePath
    }
}
