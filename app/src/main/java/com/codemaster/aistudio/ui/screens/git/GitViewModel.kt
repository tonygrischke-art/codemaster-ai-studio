package com.codemaster.aistudio.ui.screens.git

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.repository.GitRepository
import com.codemaster.aistudio.data.repository.GitStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class GitUiState(
    val status: GitStatus = GitStatus(),
    val commitMessage: String = "",
    val isLoading: Boolean = false,
    val log: String = "",
    val result: String? = null,
    val error: String? = null,
    val repoPath: String = ""
)

@HiltViewModel
class GitViewModel @Inject constructor(
    private val gitRepository: GitRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    fun init(projectId: Long) {
        // Find repo - check Termux home first, then app files dir
        val paths = listOf(
            "/data/data/com.termux/files/home/codemaster-ai-studio",
            context.filesDir.absolutePath
        )
        val repoPath = paths.firstOrNull { File(it, ".git").exists() } ?: paths.first()
        _uiState.value = _uiState.value.copy(repoPath = repoPath)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = gitRepository.getStatus(_uiState.value.repoPath)
            result.fold(
                onSuccess = { status ->
                    val logResult = gitRepository.log(_uiState.value.repoPath)
                    _uiState.value = _uiState.value.copy(
                        status = status,
                        log = logResult.getOrElse { "" },
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
            )
        }
    }

    fun updateCommitMessage(msg: String) { _uiState.value = _uiState.value.copy(commitMessage = msg) }

    fun stageAndCommit() {
        val msg = _uiState.value.commitMessage.trim()
        if (msg.isBlank()) { _uiState.value = _uiState.value.copy(error = "Commit message cannot be empty"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            gitRepository.stageAll(_uiState.value.repoPath).fold(
                onSuccess = {
                    gitRepository.commit(_uiState.value.repoPath, msg).fold(
                        onSuccess = { out ->
                            _uiState.value = _uiState.value.copy(isLoading = false, result = "Committed: $out", commitMessage = "")
                            refresh()
                        },
                        onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
                    )
                },
                onFailure = { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun push() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, result = null)
            gitRepository.push(_uiState.value.repoPath).fold(
                onSuccess = { out -> _uiState.value = _uiState.value.copy(isLoading = false, result = "Pushed! " + out) },
                onFailure = { e  -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun pull() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, result = null)
            gitRepository.pull(_uiState.value.repoPath).fold(
                onSuccess = { out -> _uiState.value = _uiState.value.copy(isLoading = false, result = out); refresh() },
                onFailure = { e  -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
            )
        }
    }

    fun clearMessages() { _uiState.value = _uiState.value.copy(result = null, error = null) }
}
