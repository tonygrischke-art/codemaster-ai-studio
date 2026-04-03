package com.codemaster.aistudio.ui.screens.home

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.ProjectRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import com.codemaster.aistudio.data.util.PlatformHelper
import com.codemaster.aistudio.data.util.SafHelper
import com.codemaster.aistudio.ui.overlay.FloatingNotepadService
import com.codemaster.aistudio.ui.base.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showNewProjectDialog: Boolean = false,
    val newProjectName: String = "",
    val newProjectLanguage: String = "Kotlin",
    val newProjectDescription: String = "",
    val showImportDialog: Boolean = false,
    val showFileBrowserDialog: Boolean = false,
    val importPath: String = "",
    val showAiBuilderDialog: Boolean = false,
    val aiBuilderPrompt: String = "",
    val aiBuilderLanguage: String = "Kotlin",
    val isAiBuilding: Boolean = false,
    val aiBuilderResult: String? = null,
    val showUploadDialog: Boolean = false,
    val codeInput: String = "",
    val selectedLanguage: String = "Kotlin",
    val currentProjectId: Long? = null,
    val isProcessingCode: Boolean = false,
    val showConvertDialog: Boolean = false,
    val convertTargetLanguage: String = "Python",
    val showTemplateDialog: Boolean = false,
    val selectedTemplate: String? = null,
    val isExportingDoc: Boolean = false,
    val isExportingApk: Boolean = false,
    val groqApiKey: String = ""
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val fsRepository: FileSystemRepository,
    private val aiRepository: AiRepository,
    private val settingsRepository: SettingsRepository,
    private val platformHelper: PlatformHelper,
    val safHelper: SafHelper,
    @ApplicationContext private val context: Context
) : BaseViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    override fun handleException(throwable: Throwable) {
        _uiState.value = _uiState.value.copy(error = throwable.message)
    }

    init {
        viewModelScope.launch {
            val apiKey = settingsRepository.getApiKey()
            projectRepository.getAllProjects()
                .catch { e -> _uiState.value = _uiState.value.copy(error = e.message, isLoading = false, groqApiKey = apiKey) }
                .collect { projects -> _uiState.value = _uiState.value.copy(projects = projects, isLoading = false, groqApiKey = apiKey) }
        }
    }

    fun showNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = true, newProjectName = "", newProjectLanguage = "Kotlin", newProjectDescription = "") }
    fun hideNewProjectDialog() { _uiState.value = _uiState.value.copy(showNewProjectDialog = false) }
    fun updateProjectName(n: String) { _uiState.value = _uiState.value.copy(newProjectName = n) }
    fun updateProjectLanguage(l: String) { _uiState.value = _uiState.value.copy(newProjectLanguage = l) }
    fun updateProjectDescription(d: String) { _uiState.value = _uiState.value.copy(newProjectDescription = d) }
    fun showImportDialog() { _uiState.value = _uiState.value.copy(showImportDialog = true) }
    fun hideImportDialog() { _uiState.value = _uiState.value.copy(showImportDialog = false) }
    fun showFileBrowserDialog() { _uiState.value = _uiState.value.copy(showFileBrowserDialog = true) }
    fun hideFileBrowserDialog() { _uiState.value = _uiState.value.copy(showFileBrowserDialog = false) }
    fun updateImportPath(p: String) { _uiState.value = _uiState.value.copy(importPath = p) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
    fun showAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = true, aiBuilderPrompt = "", aiBuilderResult = null) }
    fun hideAiBuilder() { _uiState.value = _uiState.value.copy(showAiBuilderDialog = false) }
    fun updateAiBuilderPrompt(p: String) { _uiState.value = _uiState.value.copy(aiBuilderPrompt = p) }
    fun updateAiBuilderLanguage(l: String) { _uiState.value = _uiState.value.copy(aiBuilderLanguage = l) }

    fun toggleFloatingNotepad(context: Context) {
        try {
            val intent = Intent(context, FloatingNotepadService::class.java)
            if (FloatingNotepadService.isRunning) {
                context.stopService(intent)
                Toast.makeText(context, "Notepad hidden", Toast.LENGTH_SHORT).show()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Toast.makeText(context, "Floating Notepad enabled", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun showRecentProjectsDialog(context: Context) {
        val projects = _uiState.value.projects
        if (projects.isEmpty()) {
            Toast.makeText(context, "No recent projects", Toast.LENGTH_SHORT).show()
        }
    }

    fun showUploadDialog() { _uiState.value = _uiState.value.copy(showUploadDialog = true) }
    fun hideUploadDialog() { _uiState.value = _uiState.value.copy(showUploadDialog = false) }
    fun loadCode(code: String, language: String) {
        _uiState.value = _uiState.value.copy(codeInput = code, selectedLanguage = language, showUploadDialog = false)
    }

    fun updateCodeInput(code: String) { _uiState.value = _uiState.value.copy(codeInput = code) }
    fun updateSelectedLanguage(lang: String) { _uiState.value = _uiState.value.copy(selectedLanguage = lang) }

    fun editCodeWithAI(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "Enter code first", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingCode = true)
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = "Edit and improve this ${_uiState.value.selectedLanguage} code:\n\n$code\n\nProvide the improved version.",
                systemPrompt = "You are an expert code editor. Edit, improve and return the code."
            )
            result.fold(
                onSuccess = { response ->
                    val editedCode = extractCode(response)
                    _uiState.value = _uiState.value.copy(codeInput = editedCode, isProcessingCode = false)
                    Toast.makeText(context, "Code edited", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, isProcessingCode = false)
                }
            )
        }
    }

    fun correctCodeWithAI(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "Enter code first", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingCode = true)
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = "Correct and fix any errors in this ${_uiState.value.selectedLanguage} code:\n\n$code\n\nReturn the corrected code.",
                systemPrompt = "You are an expert code corrector. Fix errors and return corrected code."
            )
            result.fold(
                onSuccess = { response ->
                    val correctedCode = extractCode(response)
                    _uiState.value = _uiState.value.copy(codeInput = correctedCode, isProcessingCode = false)
                    Toast.makeText(context, "Code corrected", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, isProcessingCode = false)
                }
            )
        }
    }

    fun buildCode(context: Context) {
        val code = _uiState.value.codeInput
        val language = _uiState.value.selectedLanguage
        if (code.isBlank()) {
            Toast.makeText(context, "Enter code first", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingCode = true)
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = "Build this $language code and explain the build process:\n\n$code",
                systemPrompt = "You are an expert build engineer. Explain how to build the code."
            )
            result.fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(isProcessingCode = false)
                    Toast.makeText(context, "Build info generated", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, isProcessingCode = false)
                }
            )
        }
    }

    fun runCode(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "Enter code first", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingCode = true)
            val language = _uiState.value.selectedLanguage
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = "Explain how to run this $language code and provide any necessary commands:\n\n$code",
                systemPrompt = "You are an expert developer. Explain how to run the code."
            )
            result.fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(isProcessingCode = false)
                    Toast.makeText(context, "Run instructions generated", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, isProcessingCode = false)
                }
            )
        }
    }

    fun exportAsDocument(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "No code to export", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExportingDoc = true)
            try {
                val docContent = buildDocumentContent(code, _uiState.value.selectedLanguage)
                val file = File(context.cacheDir, "code_export_${System.currentTimeMillis()}.txt")
                FileWriter(file).use { it.write(docContent) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Code Export - ${_uiState.value.selectedLanguage}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Code"))
                _uiState.value = _uiState.value.copy(isExportingDoc = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Export failed: ${e.message}", isExportingDoc = false)
            }
        }
    }

    private fun buildDocumentContent(code: String, language: String): String {
        return buildString {
            appendLine("=" .repeat(50))
            appendLine("CODE EXPORT - $language")
            appendLine("=" .repeat(50))
            appendLine()
            appendLine("Generated by CodeMaster AI Studio")
            appendLine("Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine()
            appendLine("-".repeat(50))
            appendLine("CODE CONTENT")
            appendLine("-".repeat(50))
            appendLine()
            appendLine(code)
            appendLine()
            appendLine("-".repeat(50))
            appendLine("END OF DOCUMENT")
            appendLine("=".repeat(50))
        }
    }

    fun exportAsApk(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "No code to build APK", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExportingApk = true)
            try {
                val result = aiRepository.sendMessage(
                    history = emptyList(),
                    userMessage = "Generate a complete Android project structure for this code:\n\n$code\n\nProvide all necessary build files (build.gradle, AndroidManifest.xml, etc.)",
                    systemPrompt = "You are an expert Android developer. Generate complete project files."
                )
                result.fold(
                    onSuccess = { response ->
                        _uiState.value = _uiState.value.copy(isExportingApk = false)
                        Toast.makeText(context, "APK build files generated", Toast.LENGTH_LONG).show()
                    },
                    onFailure = { e ->
                        _uiState.value = _uiState.value.copy(error = e.message, isExportingApk = false)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "APK export failed: ${e.message}", isExportingApk = false)
            }
        }
    }

    fun convertCodeLanguage(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "Enter code first", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessingCode = true, showConvertDialog = false)
            val targetLang = _uiState.value.convertTargetLanguage
            val result = aiRepository.sendMessage(
                history = emptyList(),
                userMessage = "Convert this code from ${_uiState.value.selectedLanguage} to $targetLang:\n\n$code\n\nProvide the converted code only.",
                systemPrompt = "You are an expert code converter. Convert code to the target language."
            )
            result.fold(
                onSuccess = { response ->
                    val convertedCode = extractCode(response)
                    _uiState.value = _uiState.value.copy(
                        codeInput = convertedCode,
                        selectedLanguage = targetLang,
                        isProcessingCode = false
                    )
                    Toast.makeText(context, "Converted to $targetLang", Toast.LENGTH_SHORT).show()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message, isProcessingCode = false)
                }
            )
        }
    }

    fun exportAsTemplate(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "No code to export", Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            try {
                val templateContent = buildTemplateContent(code, _uiState.value.selectedLanguage)
                val file = File(context.cacheDir, "template_${System.currentTimeMillis()}.txt")
                FileWriter(file).use { it.write(templateContent) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Code Template - ${_uiState.value.selectedLanguage}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Template"))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Template export failed: ${e.message}")
            }
        }
    }

    private fun buildTemplateContent(code: String, language: String): String {
        return buildString {
            appendLine("/* TEMPLATE: $language */")
            appendLine()
            appendLine("// Component 1: Main Code")
            appendLine(code)
            appendLine()
            appendLine("// Component 2: Dependencies")
            appendLine("// Add your dependencies here")
            appendLine()
            appendLine("// Component 3: Configuration")
            appendLine("// Add your configuration here")
        }
    }

    fun shareCode(context: Context) {
        val code = _uiState.value.codeInput
        if (code.isBlank()) {
            Toast.makeText(context, "No code to share", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val shareText = buildString {
                appendLine("Code (${_uiState.value.selectedLanguage}):")
                appendLine()
                appendLine(code)
                appendLine()
                appendLine("Shared via CodeMaster AI Studio")
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Code Share - ${_uiState.value.selectedLanguage}")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share via"))
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Share failed: ${e.message}")
        }
    }

    fun showConvertDialog() { _uiState.value = _uiState.value.copy(showConvertDialog = true) }
    fun hideConvertDialog() { _uiState.value = _uiState.value.copy(showConvertDialog = false) }
    fun updateConvertTargetLanguage(lang: String) { _uiState.value = _uiState.value.copy(convertTargetLanguage = lang) }

    fun showTemplateDialog() { _uiState.value = _uiState.value.copy(showTemplateDialog = true) }
    fun hideTemplateDialog() { _uiState.value = _uiState.value.copy(showTemplateDialog = false) }
    fun updateSelectedTemplate(template: CodeTemplate) { _uiState.value = _uiState.value.copy(selectedTemplate = template.name) }

    fun useTemplate(template: CodeTemplate) {
        viewModelScope.launch {
            val templateCode = generateTemplateCode(template)
            _uiState.value = _uiState.value.copy(
                codeInput = templateCode,
                selectedLanguage = template.categories.firstOrNull() ?: "Kotlin"
            )
        }
    }

    fun applyTemplate() {
        val templateName = _uiState.value.selectedTemplate ?: return
        val template = getTemplates().find { it.name == templateName } ?: return
        useTemplate(template)
    }

    private fun generateTemplateCode(template: CodeTemplate): String {
        return when (template.name) {
            "Android App" -> """
package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    Text("Hello Android App!")
                }
            }
        }
    }
}
            """.trimIndent()
            "React Native" -> """
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

export default function App() {
  return (
    <View style={styles.container}>
      <Text>Hello React Native!</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
});
            """.trimIndent()
            "Python Script" -> """
#!/usr/bin/env python3
'''
Python Script Template
'''

def main():
    print("Hello Python!")
    # Add your code here

if __name__ == "__main__":
    main()
            """.trimIndent()
            "Web App" -> """
<!DOCTYPE html>
<html>
<head>
    <title>Web App</title>
    <style>
        body { font-family: sans-serif; padding: 20px; }
    </style>
</head>
<body>
    <h1>Hello Web App!</h1>
    <script>
        console.log("JavaScript loaded");
    </script>
</body>
</html>
            """.trimIndent()
            "Firebase" -> """
// Firebase Configuration
const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "your-app.firebaseapp.com",
  projectId: "your-app",
  storageBucket: "your-app.appspot.com",
};

// Initialize Firebase
// import { initializeApp } from "firebase/app";
// const app = initializeApp(firebaseConfig);
            """.trimIndent()
            "API Server" -> """
// API Server Template
const express = require('express');
const app = express();

app.get('/', (req, res) => {
  res.json({ message: 'API Server Running' });
});

app.listen(3000, () => {
  console.log('Server running on port 3000');
});
            """.trimIndent()
            else -> "// Add your code for ${template.name}\n"
        }
    }

    private fun extractCode(response: String): String {
        val codeBlockPattern = Regex("```(?:\\w+)?\\n([\\s\\S]*?)```")
        val match = codeBlockPattern.find(response)
        return match?.groupValues?.get(1)?.trim() ?: response.trim()
    }

    fun buildAppWithAi(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.aiBuilderPrompt.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAiBuilding = true)
            val systemPrompt = buildString {
                append("You are an expert ${state.aiBuilderLanguage} developer. ")
                append("Generate complete working code in this format:\n")
                append("NAME: YourAppName\n")
                append("DESC: description\n")
                append("FILE: filename\n")
                append("<<<\ncontent\n>>>\n")
            }
            val userPrompt = "Build a complete ${state.aiBuilderLanguage} project: ${state.aiBuilderPrompt}"
            val result = aiRepository.sendMessage(history = emptyList(), userMessage = userPrompt, systemPrompt = systemPrompt)
            result.fold(
                onSuccess = { response ->
                    try {
                        val lines = response.lines()
                        val projectName = lines.firstOrNull { it.startsWith("NAME:") }?.removePrefix("NAME:")?.trim()?.ifBlank { "AI-Project" } ?: "AI-Project"
                        val description = lines.firstOrNull { it.startsWith("DESC:") }?.removePrefix("DESC:")?.trim() ?: state.aiBuilderPrompt
                        val projectPath = platformHelper.createProjectDirectory(projectName)
                        var currentFileName = ""
                        var isCollecting = false
                        val currentContent = StringBuilder()
                        for (line in lines) {
                            when {
                                line.startsWith("FILE:") -> {
                                    if (currentFileName.isNotBlank() && currentContent.isNotBlank()) {
                                        val file = File(projectPath, currentFileName)
                                        file.parentFile?.mkdirs()
                                        file.writeText(currentContent.toString().trimEnd())
                                    }
                                    currentFileName = line.removePrefix("FILE:").trim()
                                    currentContent.clear()
                                    isCollecting = false
                                }
                                line.trim() == "<<<" -> isCollecting = true
                                line.trim() == ">>>" -> isCollecting = false
                                isCollecting -> currentContent.appendLine(line)
                            }
                        }
                        if (currentFileName.isNotBlank() && currentContent.isNotBlank()) {
                            val file = File(projectPath, currentFileName)
                            file.parentFile?.mkdirs()
                            file.writeText(currentContent.toString().trimEnd())
                        }
                        val id = projectRepository.createProject(projectName, state.aiBuilderLanguage, "Loaded from: $projectPath")
                        projectRepository.getProjectById(id)?.let { projectRepository.updateProject(it.copy(path = projectPath)) }
                        _uiState.value = _uiState.value.copy(isAiBuilding = false, aiBuilderResult = "Created $projectName at $projectPath")
                        hideAiBuilder()
                        onCreated(id)
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(isAiBuilding = false, error = "AI Builder error: ${e.message}")
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isAiBuilding = false, error = "AI error: ${e.message}")
                }
            )
        }
    }

    fun createProject(onCreated: (Long) -> Unit) {
        val state = _uiState.value
        if (state.newProjectName.isBlank()) return
        viewModelScope.launch {
            val id = projectRepository.createProject(state.newProjectName, state.newProjectLanguage, state.newProjectDescription)
            hideNewProjectDialog()
            onCreated(id)
        }
    }

    fun importProject(onImported: (Long) -> Unit) {
        val path = _uiState.value.importPath.trim()
        if (path.isBlank()) return
        viewModelScope.launch {
            val name = path.substringAfterLast("/").ifBlank { "Imported Project" }
            val id = projectRepository.createProject(name, "Kotlin", "Loaded from: $path")
            projectRepository.getProjectById(id)?.let { projectRepository.updateProject(it.copy(path = path)) }
            hideImportDialog()
            onImported(id)
        }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch { projectRepository.deleteProject(project) }
    }
}