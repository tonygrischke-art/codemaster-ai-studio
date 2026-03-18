package com.codemaster.aistudio.ui.screens.snippets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemaster.aistudio.data.dao.SnippetDao
import com.codemaster.aistudio.data.model.Snippet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SnippetUiState(
    val snippets: List<Snippet> = emptyList(),
    val query: String = "",
    val showAddDialog: Boolean = false,
    val editingSnippet: Snippet? = null,
    val newTitle: String = "",
    val newCode: String = "",
    val newLanguage: String = "kotlin",
    val newTags: String = ""
)

@HiltViewModel
class SnippetViewModel @Inject constructor(
    private val snippetDao: SnippetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SnippetUiState())
    val uiState: StateFlow<SnippetUiState> = _uiState.asStateFlow()

    init { loadSnippets() }

    private fun loadSnippets() {
        viewModelScope.launch {
            snippetDao.getAllSnippets().catch { }.collect { snippets ->
                _uiState.value = _uiState.value.copy(snippets = snippets)
            }
        }
    }

    fun search(q: String) {
        _uiState.value = _uiState.value.copy(query = q)
        viewModelScope.launch {
            if (q.isBlank()) snippetDao.getAllSnippets()
            else snippetDao.searchSnippets(q)
                .catch { }.collect { snippets ->
                    _uiState.value = _uiState.value.copy(snippets = snippets)
                }
        }
    }

    fun showAddDialog(snippet: Snippet? = null) {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingSnippet = snippet,
            newTitle = snippet?.title ?: "",
            newCode = snippet?.code ?: "",
            newLanguage = snippet?.language ?: "kotlin",
            newTags = snippet?.tags ?: ""
        )
    }

    fun hideDialog() { _uiState.value = _uiState.value.copy(showAddDialog = false, editingSnippet = null) }
    fun updateTitle(v: String)    { _uiState.value = _uiState.value.copy(newTitle = v) }
    fun updateCode(v: String)     { _uiState.value = _uiState.value.copy(newCode = v) }
    fun updateLanguage(v: String) { _uiState.value = _uiState.value.copy(newLanguage = v) }
    fun updateTags(v: String)     { _uiState.value = _uiState.value.copy(newTags = v) }

    fun saveSnippet() {
        val s = _uiState.value
        if (s.newTitle.isBlank() || s.newCode.isBlank()) return
        viewModelScope.launch {
            val snippet = s.editingSnippet?.copy(
                title = s.newTitle, code = s.newCode,
                language = s.newLanguage, tags = s.newTags
            ) ?: Snippet(title = s.newTitle, code = s.newCode, language = s.newLanguage, tags = s.newTags)
            if (s.editingSnippet != null) snippetDao.updateSnippet(snippet)
            else snippetDao.insertSnippet(snippet)
            hideDialog()
        }
    }

    fun deleteSnippet(snippet: Snippet) {
        viewModelScope.launch { snippetDao.deleteSnippet(snippet) }
    }
}
