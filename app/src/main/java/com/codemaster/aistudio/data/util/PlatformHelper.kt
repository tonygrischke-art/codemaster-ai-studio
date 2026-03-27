package com.codemaster.aistudio.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val safHelper: SafHelper
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
        get() = when {
            safHelper.hasPersistedAccess() -> "saf://root"
            isTermuxAvailable -> TERMUX_HOME
            else -> context.filesDir.absolutePath
        }

    val documentsDirectory: String
        get() = when {
            safHelper.hasPersistedAccess() -> "saf://root"
            isTermuxAvailable -> "$TERMUX_HOME/Documents"
            else -> context.getExternalFilesDir(null)?.absolutePath ?: "${context.filesDir.absolutePath}/Documents"
        }

    val isUsingSaf: Boolean
        get() = safHelper.hasPersistedAccess()

    val gitBinary: String?
        get() = when {
            isTermuxAvailable -> "$TERMUX_USR_BIN/git"
            else -> null
        }

    val bashBinary: String?
        get() = when {
            isTermuxAvailable -> "$TERMUX_USR_BIN/bash"
            else -> "/system/bin/sh"
        }

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
        return if (safHelper.hasPersistedAccess()) {
            "saf://root/$projectName"
        } else {
            val baseDir = documentsDirectory
            val projectDir = File(baseDir, projectName)
            if (!projectDir.exists()) projectDir.mkdirs()
            projectDir.absolutePath
        }
    }

    fun isTermuxGitAvailable(): Boolean = gitBinary != null

    fun getGitInstallInstructions(): String = 
        if (isTermuxAvailable) {
            "Run in Termux: pkg install git"
        } else {
            "Install Termux from F-Droid or Play Store, then run: pkg install git"
        }
}