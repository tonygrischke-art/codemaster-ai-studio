package com.codemaster.aistudio

import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.codemaster.aistudio.data.util.SafHelper
import com.codemaster.aistudio.ui.navigation.NavGraph
import com.codemaster.aistudio.ui.screens.settings.SettingsViewModel
import com.codemaster.aistudio.ui.theme.CodeMasterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"

    @Inject
    lateinit var safHelper: SafHelper

    // Reactive flag — updated immediately after directory pick, no recreate() needed
    private var hasDirectoryAccess = mutableStateOf(false)

    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val granted = safHelper.onDirectoryPicked(uri)
                if (granted) {
                    hasDirectoryAccess.value = true
                } else {
                    android.widget.Toast.makeText(
                        this,
                        "Directory permission was not granted. Please try again.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialise the reactive flag from persisted state
        hasDirectoryAccess.value = safHelper.hasPersistedAccess()

        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            val hasAccess by hasDirectoryAccess

            CodeMasterTheme(darkTheme = settingsState.isDarkTheme) {
                if (hasAccess) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController, safHelper = safHelper)
                } else {
                    PermissionScreen(
                        onSelectDirectory = { launchDirectoryPicker() }
                    )
                }
            }
        }
    }

    private fun launchDirectoryPicker() {
        try {
            val intent = safHelper.createDocumentPickerIntent()
            directoryPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch directory picker", e)
        }
    }

    override fun onResume() {
        super.onResume()
    }
}

@Composable
fun PermissionScreen(
    onSelectDirectory: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📁",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "Select Project Directory",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "CodeMaster uses Android's Storage Access Framework.\n\n" +
                        "• Tap below to select your projects folder\n" +
                        "• Your selection is remembered automatically\n" +
                        "• Works on Android 11+ (API 30+)\n" +
                        "• No storage permissions required",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSelectDirectory,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select Directory")
            }
            Text(
                text = "All AI-generated files will be saved here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}