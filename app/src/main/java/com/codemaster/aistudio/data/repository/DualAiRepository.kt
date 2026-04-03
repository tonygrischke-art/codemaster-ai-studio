package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.api.ClaudeApiService
import com.codemaster.aistudio.data.api.ClaudeChatRequest
import com.codemaster.aistudio.data.api.ClaudeMessage
import com.codemaster.aistudio.data.api.KimiApiService
import com.codemaster.aistudio.data.api.KimiChatRequest
import com.codemaster.aistudio.data.api.KimiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DualAiProgress(
    val stage: Stage,
    val message: String,
    val claudeVision: String? = null,
    val kimiVision: String? = null,
    val mergedSpec: String? = null,
    val generatedFiles: Map<String, String> = emptyMap(),
    val totalTokens: Int = 0,
    val estimatedCost: String = "$0.00",
    val error: String? = null
) {
    enum class Stage {
        STARTING,
        CLAUDE_THINKING,
        KIMI_THINKING,
        COLLABORATING,
        MERGING,
        GENERATING,
        WRITING_FILES,
        COMPLETE,
        ERROR
    }
}

@Singleton
class DualAiRepository @Inject constructor(
    private val claudeApiService: ClaudeApiService,
    private val kimiApiService: KimiApiService,
    private val settingsRepository: SettingsRepository
) {
    fun buildApp(
        userDescription: String,
        language: String,
        outputPath: String
    ): Flow<DualAiProgress> = flow {

        val claudeKey = settingsRepository.getClaudeApiKey()
        val kimiKey = settingsRepository.getKimiApiKey()
        val groqKey = settingsRepository.getApiKey()

        val hasClaudeKey = claudeKey.isNotBlank()
        val hasKimiKey = kimiKey.isNotBlank()
        val hasGroqKey = groqKey.isNotBlank()

        if (!hasClaudeKey && !hasKimiKey && !hasGroqKey) {
            emit(DualAiProgress(DualAiProgress.Stage.ERROR, "No AI keys configured. Go to Settings.", error = "No API keys"))
            return@flow
        }

        emit(DualAiProgress(DualAiProgress.Stage.STARTING, "Initializing Dual AI collaboration engine..."))

        val visionPrompt = buildString {
            appendLine("You are an elite software architect. A user wants to build the following app:")
            appendLine()
            appendLine("\"$userDescription\"")
            appendLine()
            appendLine("Provide your complete vision for this app covering:")
            appendLine("1. FEATURES: Complete feature list with priorities")
            appendLine("2. TECH STACK: Languages, frameworks, libraries, databases")
            appendLine("3. ARCHITECTURE: System design, patterns, data flow")
            appendLine("4. UI/UX: Screen layouts, user journeys, design principles")
            appendLine("5. SECURITY: Auth, encryption, data protection")
            appendLine("6. SCALABILITY: How it grows from 100 to 1M users")
            appendLine("7. UNIQUE ADVANTAGES: What makes this app special")
            appendLine()
            append("Be specific, creative, and think beyond what was asked. This is your chance to design something exceptional.")
        }

        var claudeVision = ""
        var kimiVision = ""
        var totalTokens = 0

        // Phase 1: Independent visions (parallel if both keys available)
        if (hasClaudeKey && hasKimiKey) {
            emit(DualAiProgress(DualAiProgress.Stage.CLAUDE_THINKING, "Claude and Kimi are independently designing your app..."))
            coroutineScope {
                val claudeJob = async(Dispatchers.IO) {
                    try {
                        val response = claudeApiService.chat(
                            apiKey = claudeKey,
                            version = "2023-06-01",
                            contentType = "application/json",
                            request = ClaudeChatRequest(
                                model = "claude-opus-4-5-20251101",
                                max_tokens = 8000,
                                system = "You are Claude, an expert software architect and senior developer.",
                                messages = listOf(ClaudeMessage("user", visionPrompt))
                            )
                        )
                        response.content.firstOrNull()?.text ?: ""
                    } catch (e: Exception) { "Claude vision unavailable: ${e.message}" }
                }
                val kimiJob = async(Dispatchers.IO) {
                    try {
                        val response = kimiApiService.chat(
                            auth = "Bearer $kimiKey",
                            request = KimiChatRequest(
                                model = "moonshot-v1-128k",
                                messages = listOf(
                                    KimiMessage("system", "You are Kimi, an expert software architect and senior developer."),
                                    KimiMessage("user", visionPrompt)
                                )
                            )
                        )
                        response.choices.firstOrNull()?.message?.content ?: ""
                    } catch (e: Exception) { "Kimi vision unavailable: ${e.message}" }
                }
                claudeVision = claudeJob.await()
                kimiVision = kimiJob.await()
                totalTokens += estimateTokens(claudeVision) + estimateTokens(kimiVision)
            }
        } else if (hasClaudeKey) {
            emit(DualAiProgress(DualAiProgress.Stage.CLAUDE_THINKING, "Claude is designing your app..."))
            claudeVision = withContext(Dispatchers.IO) {
                try {
                    val response = claudeApiService.chat(
                        apiKey = claudeKey,
                        version = "2023-06-01",
                        contentType = "application/json",
                        request = ClaudeChatRequest(
                            model = "claude-opus-4-5-20251101",
                            max_tokens = 8000,
                            system = "You are Claude, an expert software architect.",
                            messages = listOf(ClaudeMessage("user", visionPrompt))
                        )
                    )
                    response.content.firstOrNull()?.text ?: ""
                } catch (e: Exception) { "Error: ${e.message}" }
            }
            kimiVision = claudeVision // Use same vision for both
            totalTokens += estimateTokens(claudeVision)
        } else if (hasKimiKey) {
            emit(DualAiProgress(DualAiProgress.Stage.KIMI_THINKING, "Kimi is designing your app..."))
            kimiVision = withContext(Dispatchers.IO) {
                try {
                    val response = kimiApiService.chat(
                        auth = "Bearer $kimiKey",
                        request = KimiChatRequest(
                            model = "moonshot-v1-128k",
                            messages = listOf(
                                KimiMessage("system", "You are Kimi, an expert software architect."),
                                KimiMessage("user", visionPrompt)
                            )
                        )
                    )
                    response.choices.firstOrNull()?.message?.content ?: ""
                } catch (e: Exception) { "Error: ${e.message}" }
            }
            claudeVision = kimiVision
            totalTokens += estimateTokens(kimiVision)
        }

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.COLLABORATING,
            message = "AIs are comparing and merging their visions...",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            totalTokens = totalTokens
        ))

        // Phase 2: Collaboration - merge the two visions
        val collaborationPrompt = buildString {
            appendLine("Two expert AI architects have independently designed the same app.")
            appendLine("Your job is to merge their visions into one superior specification.")
            appendLine()
            appendLine("USER REQUEST: \"$userDescription\"")
            appendLine()
            appendLine("=== CLAUDE'S VISION ===")
            appendLine(claudeVision.take(3000))
            appendLine()
            appendLine("=== KIMI'S VISION ===")
            appendLine(kimiVision.take(3000))
            appendLine()
            appendLine("Create the ULTIMATE merged specification:")
            appendLine("1. Explicitly identify the strongest elements from each vision")
            appendLine("2. Resolve any conflicts by choosing the superior approach")
            appendLine("3. Combine into one definitive spec that's better than either alone")
            appendLine("4. List the final tech stack, architecture, and feature set")
            appendLine()
            append("Format as a clear, detailed specification that will guide code generation.")
        }

        var mergedSpec = ""
        withContext(Dispatchers.IO) {
            try {
                if (hasClaudeKey) {
                    val response = claudeApiService.chat(
                        apiKey = claudeKey,
                        version = "2023-06-01",
                        contentType = "application/json",
                        request = ClaudeChatRequest(
                            model = "claude-opus-4-5-20251101",
                            max_tokens = 8000,
                            system = "You are a master architect synthesizing the best ideas from multiple experts.",
                            messages = listOf(ClaudeMessage("user", collaborationPrompt))
                        )
                    )
                    mergedSpec = response.content.firstOrNull()?.text ?: ""
                } else if (hasKimiKey) {
                    val response = kimiApiService.chat(
                        auth = "Bearer $kimiKey",
                        request = KimiChatRequest(
                            messages = listOf(
                                KimiMessage("system", "You are a master architect synthesizing the best ideas from multiple experts."),
                                KimiMessage("user", collaborationPrompt)
                            )
                        )
                    )
                    mergedSpec = response.choices.firstOrNull()?.message?.content ?: ""
                }
                totalTokens += estimateTokens(mergedSpec)
            } catch (e: Exception) {
                mergedSpec = "Merged spec generation failed: ${e.message}\n\nUsing individual visions."
            }
        }

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.GENERATING,
            message = "Generating complete production-ready code...",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            mergedSpec = mergedSpec,
            totalTokens = totalTokens
        ))

        // Phase 3: Code generation from merged spec
        val codePrompt = buildString {
            appendLine("Based on this specification, generate a COMPLETE, PRODUCTION-READY $language application.")
            appendLine()
            appendLine("SPECIFICATION:")
            appendLine(mergedSpec.take(4000))
            appendLine()
            appendLine("REQUIREMENTS:")
            appendLine("- Generate ALL necessary files with complete, working code")
            appendLine("- Include comments explaining key sections")
            appendLine("- Follow $language best practices and conventions")
            appendLine("- Include a README.md with setup and run instructions")
            appendLine("- Include basic tests where appropriate")
            appendLine("- Code must be immediately runnable")
            appendLine()
            appendLine("Use EXACTLY this format for each file (no other text):")
            appendLine("FILE: path/to/filename.ext")
            appendLine("<<<")
            appendLine("complete file content")
            appendLine(">>>")
            appendLine()
            append("Generate at least 5-10 complete files. Make it exceptional.")
        }

        var generatedCode = ""
        withContext(Dispatchers.IO) {
            try {
                if (hasClaudeKey) {
                    val response = claudeApiService.chat(
                        apiKey = claudeKey,
                        version = "2023-06-01",
                        contentType = "application/json",
                        request = ClaudeChatRequest(
                            model = "claude-opus-4-5-20251101",
                            max_tokens = 8000,
                            system = "You are an elite software engineer. Generate only code in the exact format specified. No explanations outside of file blocks.",
                            messages = listOf(ClaudeMessage("user", codePrompt))
                        )
                    )
                    generatedCode = response.content.firstOrNull()?.text ?: ""
                } else if (hasKimiKey) {
                    val response = kimiApiService.chat(
                        auth = "Bearer $kimiKey",
                        request = KimiChatRequest(
                            messages = listOf(
                                KimiMessage("system", "You are an elite software engineer. Generate only code in the exact format specified."),
                                KimiMessage("user", codePrompt)
                            )
                        )
                    )
                    generatedCode = response.choices.firstOrNull()?.message?.content ?: ""
                } else if (hasGroqKey) {
                    // Fallback to Groq
                    generatedCode = "FILE: README.md\n<<<\n# $userDescription\n\nGenerated by CodeMaster AI\nAdd Claude or Kimi API key for full code generation.\n>>>"
                }
                totalTokens += estimateTokens(generatedCode)
            } catch (e: Exception) {
                generatedCode = "Code generation failed: ${e.message}"
            }
        }

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.WRITING_FILES,
            message = "Writing files to disk...",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            mergedSpec = mergedSpec,
            totalTokens = totalTokens
        ))

        // Phase 4: Parse and write files
        val generatedFiles = mutableMapOf<String, String>()
        val lines = generatedCode.lines()
        var currentFile = ""
        var collecting = false
        val currentContent = StringBuilder()

        for (line in lines) {
            when {
                line.startsWith("FILE:") -> {
                    if (currentFile.isNotBlank() && currentContent.isNotBlank()) {
                        generatedFiles[currentFile] = currentContent.toString().trimEnd()
                        currentContent.clear()
                    }
                    currentFile = line.removePrefix("FILE:").trim()
                    collecting = false
                }
                line.trim() == "<<<" -> collecting = true
                line.trim() == ">>>" -> collecting = false
                collecting -> currentContent.appendLine(line)
            }
        }
        // Last file
        if (currentFile.isNotBlank() && currentContent.isNotBlank()) {
            generatedFiles[currentFile] = currentContent.toString().trimEnd()
        }

        // Write to disk - create all directories first
        val baseDir = File(outputPath)
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            emit(DualAiProgress(DualAiProgress.Stage.ERROR, "Failed to create project directory", error = "Cannot create directory: $outputPath"))
            return@flow
        }
        
        var filesWritten = 0
        val writeErrors = mutableListOf<String>()
        generatedFiles.forEach { (fileName, content) ->
            try {
                val file = File(outputPath, fileName)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        writeErrors.add("Failed to create directory: ${parent.absolutePath}")
                        return@forEach
                    }
                }
                file.writeText(content)
                filesWritten++
            } catch (e: Exception) {
                writeErrors.add("Failed to write $fileName: ${e.message}")
            }
        }

        // Always write the spec as a file
        try {
            val specFile = File(outputPath, "AI_COLLABORATION_SPEC.md")
            specFile.parentFile?.mkdirs()
            specFile.writeText(buildString {
            appendLine("# AI Collaboration Spec")
            appendLine("Generated by CodeMaster AI Dual Engine")
            appendLine()
            appendLine("## User Request")
            appendLine(userDescription)
            appendLine()
            appendLine("## Claude's Vision")
            appendLine(claudeVision)
            appendLine()
            appendLine("## Kimi's Vision")
            appendLine(kimiVision)
            appendLine()
            appendLine("## Merged Specification")
            appendLine(mergedSpec)
        })
        } catch (e: Exception) {
            writeErrors.add("Failed to write spec file: ${e.message}")
        }

        // If critical errors occurred, report them
        if (writeErrors.isNotEmpty() && filesWritten == 0) {
            emit(DualAiProgress(DualAiProgress.Stage.ERROR, "Failed to write files", error = writeErrors.joinToString("; ")))
            return@flow
        }

        // Calculate cost estimate (Claude Opus + Kimi)
        val claudeCost = (totalTokens / 2 / 1000.0) * 0.015
        val kimiCost = (totalTokens / 2 / 1000.0) * 0.01
        val estimatedCost = "~$${String.format("%.4f", claudeCost + kimiCost)}"

        // Save token usage
        val previousTokens = settingsRepository.getTotalTokensUsed()
        settingsRepository.saveTotalTokensUsed(previousTokens + totalTokens)

        emit(DualAiProgress(
            stage = DualAiProgress.Stage.COMPLETE,
            message = "Your app is ready! ${generatedFiles.size} files generated.",
            claudeVision = claudeVision,
            kimiVision = kimiVision,
            mergedSpec = mergedSpec,
            generatedFiles = generatedFiles,
            totalTokens = totalTokens,
            estimatedCost = estimatedCost
        ))
    }

    private fun estimateTokens(text: String) = (text.length / 4).coerceAtLeast(1)
}
