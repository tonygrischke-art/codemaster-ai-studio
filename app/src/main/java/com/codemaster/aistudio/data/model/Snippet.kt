package com.codemaster.aistudio.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snippets")
data class Snippet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val code: String,
    val language: String = "kotlin",
    val tags: String = "",          // comma-separated
    val createdAt: Long = System.currentTimeMillis()
)
