package com.codemaster.aistudio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.codemaster.aistudio.data.CodeMasterDatabase
import com.codemaster.aistudio.data.api.GitHubApiService
import com.codemaster.aistudio.data.api.GroqApiService
import com.codemaster.aistudio.data.dao.ChatMessageDao
import com.codemaster.aistudio.data.dao.CodeFileDao
import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.dao.SnippetDao
import com.codemaster.aistudio.data.repository.FileSystemRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codemaster_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CodeMasterDatabase =
        Room.databaseBuilder(context, CodeMasterDatabase::class.java, "codemaster_db")
            .fallbackToDestructiveMigration().build()

    @Provides @Singleton fun provideProjectDao(db: CodeMasterDatabase): ProjectDao = db.projectDao()
    @Provides @Singleton fun provideChatMessageDao(db: CodeMasterDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides @Singleton fun provideCodeFileDao(db: CodeMasterDatabase): CodeFileDao = db.codeFileDao()
    @Provides @Singleton fun provideSnippetDao(db: CodeMasterDatabase): SnippetDao = db.snippetDao()

    @Provides @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides @Singleton
    fun provideFileSystemRepository(@ApplicationContext context: Context): FileSystemRepository =
        FileSystemRepository(context)

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    @Provides @Singleton @Named("groq")
    fun provideGroqRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.groq.com/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton @Named("github")
    fun provideGitHubRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/").client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()

    @Provides @Singleton
    fun provideGroqApiService(@Named("groq") retrofit: Retrofit): GroqApiService =
        retrofit.create(GroqApiService::class.java)

    @Provides @Singleton
    fun provideGitHubApiService(@Named("github") retrofit: Retrofit): GitHubApiService =
        retrofit.create(GitHubApiService::class.java)
}
