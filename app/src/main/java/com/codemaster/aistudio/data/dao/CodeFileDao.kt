package com.codemaster.aistudio.data.dao

import androidx.room.*
import com.codemaster.aistudio.data.model.CodeFile
import kotlinx.coroutines.flow.Flow

@Dao
interface CodeFileDao {
    @Query("SELECT * FROM code_files WHERE projectId = :projectId ORDER BY name ASC")
    fun getFilesForProject(projectId: Long): Flow<List<CodeFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: CodeFile): Long

    @Update
    suspend fun updateFile(file: CodeFile)

    @Delete
    suspend fun deleteFile(file: CodeFile)

    @Query("SELECT * FROM code_files WHERE id = :id")
    suspend fun getFileById(id: Long): CodeFile?
}
