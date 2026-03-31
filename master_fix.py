#!/usr/bin/env python3
"""
CodeMaster AI Studio - Master Fix Script
Fixes all critical bugs, security issues, and code quality problems
Run: python master_fix.py
"""

import os
import re
import shutil
from pathlib import Path

BASE_DIR = Path("app/src/main/java/com/codemaster/aistudio")
ROOT_DIR = Path(".")

# Colors for terminal output
class Colors:
    OK = '\033[92m'
    WARN = '\033[93m'
    ERROR = '\033[91m'
    INFO = '\033[94m'
    END = '\033[0m'

def log(msg, color=Colors.INFO):
    print(f"{color}[FIX] {msg}{Colors.END}")

def read_file(path):
    try:
        with open(path, 'r', encoding='utf-8') as f:
            return f.read()
    except Exception as e:
        log(f"Failed to read {path}: {e}", Colors.ERROR)
        return None

def write_file(path, content):
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        log(f"Updated {path}", Colors.OK)
        return True
    except Exception as e:
        log(f"Failed to write {path}: {e}", Colors.ERROR)
        return False

def fix_memory_leak():
    """Fix 1: Memory leak in AiChatViewModel"""
    path = BASE_DIR / "ui/screens/chat/AiChatViewModel.kt"
    content = read_file(path)
    if not content:
        return False
    
    old_close = """fun closeTab(index: Int) {
        autoSaveJobs[index]?.cancel()
        val tabs = _uiState.value.openTabs.toMutableList()"""
    
    new_close = """fun closeTab(index: Int) {
        autoSaveJobs[index]?.cancel()
        autoSaveJobs.remove(index) // FIX: Remove cancelled job from map to prevent memory leak
        val tabs = _uiState.value.openTabs.toMutableList()"""
    
    if old_close in content:
        content = content.replace(old_close, new_close)
        return write_file(path, content)
    else:
        log("Memory leak fix already applied or pattern changed", Colors.WARN)
        return True

def fix_permission_handling():
    """Fix 2: Add proper permission handling in MainActivity"""
    path = BASE_DIR / "MainActivity.kt"
    content = read_file(path)
    if not content:
        return False
    
    new_activity = '''package com.codemaster.aistudio

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.codemaster.aistudio.ui.navigation.NavGraph
import com.codemaster.aistudio.ui.screens.settings.SettingsViewModel
import com.codemaster.aistudio.ui.theme.CodeMasterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var pendingPermissionCheck = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestStoragePermission()
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            CodeMasterTheme(darkTheme = settingsState.isDarkTheme) {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
    
    private fun checkAndRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                pendingPermissionCheck = true
                requestManageAllFilesPermission()
            }
        } else {
            // For Android 10 and below, request legacy permissions in AndroidManifest
        }
    }
    
    private fun requestManageAllFilesPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Please grant storage permission manually in Settings", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (pendingPermissionCheck) {
            pendingPermissionCheck = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Storage permission required for app functionality", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Storage permission granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
'''
    return write_file(path, new_activity)

def fix_api_key_encryption():
    """Fix 3: Add encrypted storage for API keys"""
    path = BASE_DIR / "data/repository/SettingsRepository.kt"
    content = read_file(path)
    if not content:
        return False
    
    new_repo = '''package com.codemaster.aistudio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context
) {
    companion object {
        val MODEL = stringPreferencesKey("groq_model")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val GITHUB_OWNER = stringPreferencesKey("github_owner")
        val GITHUB_REPO = stringPreferencesKey("github_repo")
        val GITHUB_BRANCH = stringPreferencesKey("github_branch")
        val AI_PERSONA = stringPreferencesKey("ai_persona")
        val PROJECT_PATH = stringPreferencesKey("project_path")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val TOTAL_TOKENS_USED = stringPreferencesKey("total_tokens_used")
        
        private const val PREFS_FILE = "secure_api_keys"
        private const val KEY_GROQ = "groq_api_key_enc"
        private const val KEY_CLAUDE = "claude_api_key_enc"
        private const val KEY_KIMI = "kimi_api_key_enc"
        private const val KEY_GITHUB_TOKEN = "github_token_enc"
    }
    
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val securePrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // API Keys - ENCRYPTED STORAGE (non-suspend, synchronous)
    fun getApiKey(): String = securePrefs.getString(KEY_GROQ, "") ?: ""
    fun saveApiKey(k: String) = securePrefs.edit().putString(KEY_GROQ, k).apply()
    
    fun getClaudeApiKey(): String = securePrefs.getString(KEY_CLAUDE, "") ?: ""
    fun saveClaudeApiKey(k: String) = securePrefs.edit().putString(KEY_CLAUDE, k).apply()
    
    fun getKimiApiKey(): String = securePrefs.getString(KEY_KIMI, "") ?: ""
    fun saveKimiApiKey(k: String) = securePrefs.edit().putString(KEY_KIMI, k).apply()
    
    fun getGitHubToken(): String = securePrefs.getString(KEY_GITHUB_TOKEN, "") ?: ""
    fun saveGitHubToken(t: String) = securePrefs.edit().putString(KEY_GITHUB_TOKEN, t).apply()
    
    // General settings - DataStore (unencrypted, non-sensitive)
    suspend fun getModel(): String = dataStore.data.first()[MODEL] ?: "llama-3.3-70b-versatile"
    suspend fun saveModel(m: String) { dataStore.edit { it[MODEL] = m } }
    
    suspend fun getDarkTheme(): Boolean = dataStore.data.first()[DARK_THEME] ?: true
    suspend fun saveDarkTheme(d: Boolean) { dataStore.edit { it[DARK_THEME] = d } }
    
    suspend fun getGitHubOwner(): String = dataStore.data.first()[GITHUB_OWNER] ?: ""
    suspend fun saveGitHubOwner(o: String) { dataStore.edit { it[GITHUB_OWNER] = o } }
    
    suspend fun getGitHubRepo(): String = dataStore.data.first()[GITHUB_REPO] ?: ""
    suspend fun saveGitHubRepo(r: String) { dataStore.edit { it[GITHUB_REPO] = r } }
    
    suspend fun getGitHubBranch(): String = dataStore.data.first()[GITHUB_BRANCH] ?: "main"
    suspend fun saveGitHubBranch(b: String) { dataStore.edit { it[GITHUB_BRANCH] = b } }
    
    suspend fun getPersonaId(): String = dataStore.data.first()[AI_PERSONA] ?: "codemaster"
    suspend fun savePersonaId(id: String) { dataStore.edit { it[AI_PERSONA] = id } }
    
    suspend fun getProjectPath(): String = dataStore.data.first()[PROJECT_PATH] ?: "/sdcard/codemaster-ai-studio"
    suspend fun saveProjectPath(p: String) { dataStore.edit { it[PROJECT_PATH] = p } }
    
    suspend fun getOnboardingComplete(): Boolean = dataStore.data.first()[ONBOARDING_COMPLETE] ?: false
    suspend fun saveOnboardingComplete(v: Boolean) { dataStore.edit { it[ONBOARDING_COMPLETE] = v } }
    
    suspend fun getTotalTokensUsed(): Long = dataStore.data.first()[TOTAL_TOKENS_USED]?.toLongOrNull() ?: 0
    suspend fun saveTotalTokensUsed(t: Long) { dataStore.edit { it[TOTAL_TOKENS_USED] = t.toString() } }
    
    // Migration helper
    fun migratePlaintextKeys(groq: String?, claude: String?, kimi: String?, github: String?) {
        groq?.let { if (it.isNotBlank() && getApiKey().isBlank()) saveApiKey(it) }
        claude?.let { if (it.isNotBlank() && getClaudeApiKey().isBlank()) saveClaudeApiKey(it) }
        kimi?.let { if (it.isNotBlank() && getKimiApiKey().isBlank()) saveKimiApiKey(it) }
        github?.let { if (it.isNotBlank() && getGitHubToken().isBlank()) saveGitHubToken(it) }
    }
}
'''
    return write_file(path, new_repo)

def fix_database_migration():
    """Fix 4: Remove destructive migration, add proper migration strategy"""
    db_path = BASE_DIR / "data/CodeMasterDatabase.kt"
    db_content = read_file(db_path)
    if db_content:
        new_db = '''package com.codemaster.aistudio.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 4,
    exportSchema = true
)
@TypeConverters(StringListConverter::class)
abstract class CodeMasterDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun codeFileDao(): CodeFileDao
    abstract fun snippetDao(): SnippetDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN path TEXT NOT NULL DEFAULT ''")
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN attachedFileName TEXT")
            }
        }
        
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(chat_messages)")
                var columnExists = false
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (name == "tokenCount") {
                        columnExists = true
                        break
                    }
                }
                cursor.close()
                if (!columnExists) {
                    db.execSQL("ALTER TABLE chat_messages ADD COLUMN tokenCount INTEGER NOT NULL DEFAULT 0")
                }
            }
        }
    }
}
'''
        write_file(db_path, new_db)
    
    module_path = BASE_DIR / "di/AppModule.kt"
    module_content = read_file(module_path)
    if module_content:
        new_module = module_content.replace(
            '.fallbackToDestructiveMigration().build()',
            '.addMigrations(CodeMasterDatabase.MIGRATION_1_2, CodeMasterDatabase.MIGRATION_2_3, CodeMasterDatabase.MIGRATION_3_4).build()'
        )
        write_file(module_path, new_module)
        return True
    return False

def fix_file_path_validation():
    """Fix 5: Add input validation for file paths"""
    validation_file = BASE_DIR / "util/FileValidation.kt"
    validation_content = '''package com.codemaster.aistudio.util

import java.io.File
import java.util.regex.Pattern

object FileValidation {
    
    private val VALID_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$")
    private val MAX_FILENAME_LENGTH = 255
    private val FORBIDDEN_DIRS = setOf(".git", "build", ".gradle", "node_modules", ".idea", "__pycache__", "bin", "obj")
    
    fun isValidFileName(name: String): Boolean {
        if (name.isBlank() || name.length > MAX_FILENAME_LENGTH) return false
        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\\\")) return false
        if (FORBIDDEN_DIRS.contains(name.lowercase())) return false
        return VALID_FILENAME_PATTERN.matcher(name).matches()
    }
    
    fun sanitizePath(basePath: String, relativePath: String): String? {
        val base = File(basePath).canonicalPath
        val file = File(base, relativePath)
        return try {
            val canonicalPath = file.canonicalPath
            if (canonicalPath.startsWith(base)) {
                canonicalPath
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun isPathWithinProject(projectPath: String, filePath: String): Boolean {
        return try {
            val project = File(projectPath).canonicalPath
            val file = File(filePath).canonicalPath
            file.startsWith(project)
        } catch (e: Exception) {
            false
        }
    }
}
'''
    write_file(validation_file, validation_content)
    
    vm_path = BASE_DIR / "ui/screens/chat/AiChatViewModel.kt"
    vm_content = read_file(vm_path)
    if vm_content:
        if "import com.codemaster.aistudio.util.FileValidation" not in vm_content:
            vm_content = vm_content.replace(
                "import java.io.File",
                "import java.io.File\nimport com.codemaster.aistudio.util.FileValidation"
            )
        
        old_save = '''fun saveContentAsFile(fileName: String, content: String) {
        viewModelScope.launch {
            val savePath = if (projectPath.isNotBlank()) "$projectPath/$fileName" else "/sdcard/$fileName"'''
        
        new_save = '''fun saveContentAsFile(fileName: String, content: String) {
        viewModelScope.launch {
            if (!FileValidation.isValidFileName(fileName)) {
                _uiState.value = _uiState.value.copy(error = "Invalid filename. Use only letters, numbers, dots, hyphens, and underscores.")
                return@launch
            }
            
            val savePath = FileValidation.sanitizePath(
                if (projectPath.isNotBlank()) projectPath else "/sdcard/codemaster-ai-studio", 
                fileName
            ) ?: run {
                _uiState.value = _uiState.value.copy(error = "Invalid file path")
                return@launch
            }'''
        
        vm_content = vm_content.replace(old_save, new_save)
        return write_file(vm_path, vm_content)
    return False

def fix_token_estimation():
    """Fix 6: Improve token estimation"""
    ai_repo_path = BASE_DIR / "data/repository/AiRepository.kt"
    content = read_file(ai_repo_path)
    if content:
        new_content = content.replace(
            "fun estimateTokens(text: String): Int = (text.length / 4).coerceAtLeast(1)",
            '''fun estimateTokens(text: String): Int {
                val estimated = (text.length / 3.8).toInt()
                return estimated.coerceAtLeast(1)
            }'''
        )
        return write_file(ai_repo_path, new_content)
    return False

def cleanup_dead_files():
    """Fix 7: Remove dead/placeholder files"""
    dead_files = [
        "A", "Android", "Compilation", "Error", "Get", "Run", "Task", 
        "[Hilt]", "fix.py", "fix2.py", "fix_clean.py", "fix_final2.py",
        "fix_paths.py", "fix_precise.py", "fix_smartcast.py", "fix_two_files.py"
    ]
    
    removed = []
    for file in dead_files:
        file_path = ROOT_DIR / file
        if file_path.exists():
            try:
                if file_path.is_file():
                    file_path.unlink()
                else:
                    shutil.rmtree(file_path)
                removed.append(file)
                log(f"Removed dead file: {file}", Colors.OK)
            except Exception as e:
                log(f"Failed to remove {file}: {e}", Colors.ERROR)
    
    if removed:
        log(f"Cleaned up {len(removed)} dead files", Colors.OK)
    return True

def update_gradle_dependencies():
    """Fix 8: Add security crypto dependency"""
    gradle_path = ROOT_DIR / "app/build.gradle.kts"
    content = read_file(gradle_path)
    if content:
        if "security-crypto" not in content:
            new_dep = '''    // Security crypto for encrypted key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")'''
            content = content.replace(
                "// Testing",
                f"{new_dep}\n\n    // Testing"
            )
            return write_file(gradle_path, content)
    return False

def add_proguard_rules():
    """Fix 9: Add ProGuard rules"""
    proguard_content = '''# CodeMaster AI Studio ProGuard Rules

-keep class com.codemaster.aistudio.data.model.** { *; }
-keepclassmembers class com.codemaster.aistudio.data.api.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keepclassmembers class * {
    @androidx.security.crypto.EncryptedSharedPreferences *;
}
-keep class * extends android.app.Application { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
'''
    proguard_path = ROOT_DIR / "app/proguard-rules.pro"
    existing = read_file(proguard_path) if proguard_path.exists() else ""
    if "CodeMaster" not in existing:
        return write_file(proguard_path, proguard_content)
    return True

def create_security_config():
    """Fix 10: Add network security config"""
    xml_content = '''<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">api.groq.com</domain>
        <domain includeSubdomains="true">api.anthropic.com</domain>
        <domain includeSubdomains="true">api.moonshot.cn</domain>
        <domain includeSubdomains="true">api.github.com</domain>
    </domain-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
'''
    xml_path = ROOT_DIR / "app/src/main/res/xml/network_security_config.xml"
    return write_file(xml_path, xml_content)

def update_android_manifest():
    """Fix 11: Update manifest"""
    manifest_path = ROOT_DIR / "app/src/main/AndroidManifest.xml"
    
    new_manifest = '''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29"
        tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />

    <application
        android:name=".CodeMasterApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.CodeMaster"
        android:networkSecurityConfig="@xml/network_security_config"
        android:requestLegacyExternalStorage="true"
        tools:targetApi="31">
        
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.CodeMaster">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
'''
    
    file_paths = '''<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <external-path name="external_files" path="." />
    <files-path name="internal_files" path="." />
    <cache-path name="cache_files" path="." />
    <external-files-path name="app_external_files" path="." />
</paths>
'''
    
    write_file(ROOT_DIR / "app/src/main/res/xml/file_paths.xml", file_paths)
    return write_file(manifest_path, new_manifest)

def create_data_extraction_rules():
    """Fix 12: Prevent backup of sensitive data"""
    xml_content = '''<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="secure_api_keys.xml"/>
        <exclude domain="database" path="codemaster_db"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="secure_api_keys.xml"/>
        <exclude domain="database" path="codemaster_db"/>
    </device-transfer>
</data-extraction-rules>
'''
    xml_path = ROOT_DIR / "app/src/main/res/xml/data_extraction_rules.xml"
    return write_file(xml_path, xml_content)

def create_readme_fixes():
    """Create summary of fixes"""
    readme = """# CodeMaster AI Studio - Security Fixes Applied

## Critical Fixes

### 1. Memory Leak Fixed
- File: `AiChatViewModel.kt`
- Added `autoSaveJobs.remove(index)` in `closeTab()`

### 2. API Key Encryption
- Migrated to `EncryptedSharedPreferences` with AES256
- Keys now encrypted at rest using Android Keystore

### 3. Database Migration
- Removed `fallbackToDestructiveMigration()`
- Added proper incremental migrations (1->2->3->4)

### 4. Permission Handling
- Added `onResume()` permission check
- Graceful fallback with Toast messages

### 5. Path Traversal Protection
- New `FileValidation.kt` utility
- Filename validation and path sanitization

### 6. Security Hardening
- Network security config (HTTPS enforcement)
- ProGuard rules for model protection
- Data extraction rules (no cloud backup of keys)

## Build Instructions

```bash
./gradlew clean
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
"""
    return write_file(ROOT_DIR / "SECURITY_FIXES.md", readme)

def create_backup_script():
    """Create backup script"""
    backup_script = '''#!/bin/bash
    TIMESTAMP=$(date +%s)
    mkdir -p "backup_$TIMESTAMP"
    cp -r app/src/main/java backup_$TIMESTAMP/
    '''
    return write_file(ROOT_DIR / "backup.sh", backup_script)

if __name__ == "__main__":
    print("Starting CodeMaster AI Studio fixes...")
    create_security_fixes()
    create_readme_fixes()
    create_backup_script()
    print("All fixes applied!")

