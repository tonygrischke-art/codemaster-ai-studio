package com.codemaster.aistudio.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codemaster.aistudio.data.util.SafHelper
import java.io.File

/**
 * Folder picker that exposes:
 *   • App Files       — app-private external files dir
 *   • Internal Storage — app-private internal files dir
 *   • Documents       — app-private documents dir
 *   • Browse Phone... — system SAF picker (Downloads, DCIM, SD card, anywhere)
 *
 * Pass safHelper so the SAF-granted URI can be persisted and used app-wide.
 */
@Composable
fun FullStoragePickerDialog(
    safHelper: SafHelper,
    onPathSelected: (String) -> Unit,
    onSafUriSelected: (android.net.Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // System SAF directory picker launcher
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            val granted = safHelper.onDirectoryPicked(uri)
            if (granted) {
                onSafUriSelected(uri)
            }
        }
    }

    fun launchSafPicker() {
        val intent = safHelper.createDocumentPickerIntent()
        safLauncher.launch(intent)
    }

    // App-private locations (always accessible, no permissions needed)
    val locations = remember {
        buildList {
            context.getExternalFilesDir(null)?.let {
                it.mkdirs()
                add(Triple("App Files", it.absolutePath, Icons.Default.PhoneAndroid))
            }
            add(Triple("Internal Storage", context.filesDir.absolutePath, Icons.Default.Storage))
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)?.let {
                it.mkdirs()
                add(Triple("Documents", it.absolutePath, Icons.Default.Description))
            }
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.let {
                it.mkdirs()
                add(Triple("App Downloads", it.absolutePath, Icons.Default.Download))
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Select Project Folder",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose where to save and open projects.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // ── Browse entire phone (SAF) ──────────────────────────────
                // This is the key button — opens the Android system folder picker
                // which can reach Downloads, DCIM, SD card, anywhere
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            launchSafPicker()
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Browse Phone Storage",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Access Downloads, DCIM, SD card, anywhere",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "APP STORAGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // ── App-private locations ──────────────────────────────────
                locations.forEach { (name, path, icon) ->
                    StorageLocationRow(
                        name    = name,
                        path    = path,
                        icon    = icon,
                        onClick = {
                            onPathSelected(path)
                            onDismiss()
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // Cancel
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun StorageLocationRow(
    name: String,
    path: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(start = 34.dp))
}
