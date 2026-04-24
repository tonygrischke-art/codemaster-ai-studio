package com.codemaster.aistudio

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.codemaster.aistudio.databinding.ActivityMainBinding
import com.codemaster.aistudio.terminal.EmbeddedTerminalManager
import com.codemaster.aistudio.ui.adapter.MainPagerAdapter
import com.codemaster.aistudio.util.AutoPermissionManager
import com.codemaster.aistudio.service.FloatingNotepadService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: AutoPermissionManager
    private var terminalManager: EmbeddedTerminalManager? = null
    private lateinit var pagerAdapter: MainPagerAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            showPermissionRationale()
        } else {
            checkSpecialPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionManager = AutoPermissionManager(this)

        setupViewPager()
        setupFabMenu()
        setupToolbar()

        if (!permissionManager.hasAllPermissions()) {
            requestAllPermissions()
        } else {
            initializeApp()
        }
    }

    private fun setupViewPager() {
        pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 2

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Editor"
                1 -> "Terminal"
                2 -> "Projects"
                else -> ""
            }
        }.attach()
    }

    private fun setupToolbar() {
        binding.btnAiChat.setOnClickListener {
            binding.viewPager.currentItem = 0
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun setupFabMenu() {
        binding.fabMenu.setOnClickListener {
            showQuickActionMenu()
        }
    }

    private fun showQuickActionMenu() {
        val items = arrayOf(
            "New File",
            "Open Project",
            "Floating Notepad",
            "Git Clone",
            "Settings",
            "Help"
        )

        MaterialAlertDialogBuilder(this, R.style.DarkDialog)
            .setTitle("Quick Actions")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> createNewFile()
                    1 -> openProject()
                    2 -> launchFloatingNotepad()
                    3 -> showGitCloneDialog()
                    4 -> showSettingsDialog()
                    5 -> showHelp()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestAllPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.FOREGROUND_SERVICE,
                android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.VIBRATE,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.FOREGROUND_SERVICE,
                android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.VIBRATE
            )
        }

        permissionLauncher.launch(permissions.distinct().toTypedArray())
    }

    private fun checkSpecialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showSafPicker()
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        lifecycleScope.launch {
            initializeApp()
        }
    }

    private fun showSafPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        safTreeLauncher.launch(intent)
    }

    private val safTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to persist SAF permission", e)
            }
        }
    }

    private fun showPermissionRationale() {
        MaterialAlertDialogBuilder(this, R.style.DarkDialog)
            .setTitle("Permissions Required")
            .setMessage("CodeMaster AI needs storage and system permissions to function properly.")
            .setPositiveButton("Grant") { _, _ -> requestAllPermissions() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun initializeApp() {
        terminalManager = EmbeddedTerminalManager(this, lifecycleScope)
        
        lifecycleScope.launch {
            terminalManager?.preInitialize()
        }
        
        checkApiKeys()
    }

    private fun checkApiKeys() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val geminiKey = prefs.getString("gemini_api_key", null)
        val kimiKey = prefs.getString("kimi_api_key", null)

        binding.apiKeyBanner.isVisible = geminiKey.isNullOrEmpty() || kimiKey.isNullOrEmpty()
        
        binding.apiKeyBanner.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun launchFloatingNotepad() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingNotepadService::class.java))
            Toast.makeText(this, "Floating notepad opened", Toast.LENGTH_SHORT).show()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please enable 'Display over other apps'", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNewFile() {
        binding.viewPager.currentItem = 0
    }

    private fun openProject() {
        binding.viewPager.currentItem = 2
    }

    private fun showGitCloneDialog() {
        val editText = android.widget.EditText(this).apply {
            hint = "https://github.com/user/repo.git"
        }

        MaterialAlertDialogBuilder(this, R.style.DarkDialog)
            .setTitle("Git Clone")
            .setView(editText)
            .setPositiveButton("Clone") { _, _ ->
                val url = editText.text.toString()
                if (url.isNotEmpty()) {
                    lifecycleScope.launch {
                        terminalManager.executeCommand("git clone $url ~/projects/")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsDialog() {
        SettingsDialogFragment().show(supportFragmentManager, "settings")
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this, R.style.DarkDialog)
            .setTitle("CodeMaster AI Help")
            .setMessage("""
                |Editor - Write code with AI assistance
                |Terminal - Full Linux terminal (auto-installed)
                |Projects - Browse and manage files
                |
                |Tips:
                |Tap FAB for quick actions
                |Terminal has git, python, node pre-installed
                |Use floating notepad for quick notes
            """.trimMargin())
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::permissionManager.isInitialized) {
            if (!permissionManager.hasAllPermissions()) {
                val missing = permissionManager.getMissingPermissions()
                if (missing.isNotEmpty()) {
                    Toast.makeText(this, "Missing: ${missing.joinToString(", ")}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalManager?.cleanup()
    }
}
