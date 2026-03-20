package com.codemaster.aistudio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserDialog(
    startPath: String = "/sdcard",
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by remember { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf(listOf<File>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentPath) {
        try {
            val dir = File(currentPath)
            entries = dir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?: emptyList()
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Browse", fontWeight = FontWeight.Bold)
                Text(
                    currentPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                // Quick paths
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "SDCard" to "/sdcard",
                        "Termux" to "/data/data/com.termux/files/home",
                        "Up" to File(currentPath).parent
                    ).forEach { (label, path) ->
                        if (path != null) {
                            SuggestionChip(
                                onClick = { currentPath = path },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                if (error != null) {
                    Text("Cannot read: $error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(entries) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (file.isDirectory) currentPath = file.absolutePath
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    null,
                                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    file.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (file.isDirectory) {
                                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPathSelected(currentPath) }) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select This Folder")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(16.dp)
    )
}
