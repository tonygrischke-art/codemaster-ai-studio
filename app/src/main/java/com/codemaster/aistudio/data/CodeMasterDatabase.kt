package com.codemaster.aistudio.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.dao.SnippetDao
import com.codemaster.aistudio.data.model.ChatMessage
import com.codemaster.aistudio.data.model.CodeFile
import com.codemaster.aistudio.data.model.Project
import com.codemaster.aistudio.data.model.Snippet
import com.codemaster.aistudio.data.model.StringListConverter

@Database(
    entities = [Project::class, ChatMessage::class, CodeFile::class, Snippet::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class CodeMasterDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun codeFileDao(): CodeFileDao
    abstract fun snippetDao(): SnippetDao
}
