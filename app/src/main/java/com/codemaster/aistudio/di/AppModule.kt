package com.codemaster.aistudio.di

import android.content.Context
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.repository.AiRepository
import com.codemaster.aistudio.data.repository.FileSystemRepository
import com.codemaster.aistudio.data.repository.GitHubActionsRepository
import com.codemaster.aistudio.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CodeMasterDatabase {
        return CodeMasterDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository = SettingsRepository(context)

    @Provides
    @Singleton
    fun provideFileSystemRepository(
        @ApplicationContext context: Context
    ): FileSystemRepository = FileSystemRepository(context)

    @Provides
    @Singleton
    fun provideGitHubActionsRepository(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): GitHubActionsRepository = GitHubActionsRepository(context, settingsRepository)

    @Provides
    @Singleton
    fun provideAiRepository(
        settingsRepository: SettingsRepository
    ): AiRepository = AiRepository(settingsRepository)
}
