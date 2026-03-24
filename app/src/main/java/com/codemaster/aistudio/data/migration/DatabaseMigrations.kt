package com.codemaster.aistudio.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add any schema changes for version 2
            // Example: db.execSQL("ALTER TABLE projects ADD COLUMN new_field TEXT")
        }
    }
    
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add any schema changes for version 3
            // Example: db.execSQL("ALTER TABLE projects ADD COLUMN new_field TEXT")
        }
    }
}
