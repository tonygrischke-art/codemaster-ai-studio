package com.codemaster.aistudio.data.dao

import androidx.room.*
import com.codemaster.aistudio.data.model.Snippet
import kotlinx.coroutines.flow.Flow

@Dao
interface SnippetDao {
    @Query("SELECT * FROM snippets ORDER BY createdAt DESC")
    fun getAllSnippets(): Flow<List<Snippet>>

    @Query("SELECT * FROM snippets WHERE title LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%' OR tags LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun searchSnippets(q: String): Flow<List<Snippet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: Snippet): Long

    @Update
    suspend fun updateSnippet(snippet: Snippet)

    @Delete
    suspend fun deleteSnippet(snippet: Snippet)
}
