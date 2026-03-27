package com.codemaster.aistudio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codemaster.aistudio.data.util.StorageHelper
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.EntryPoint
import dagger.hilt.android.HiltAndroid
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File

@EntryPoint
@HiltAndroid
@InstallIn(SingletonComponent::class)
interface FileBrowserEntryPoint {
    fun storageHelper(): StorageHelper
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserDialog(
    startPath: String? = null,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context, FileBrowserEntryPoint::class.java)
    }
    val storageHelper = remember { entryPoint.storageHelper() }
    
    val defaultLocation = remember { storageHelper.getDefaultLocation() }
    val availableLocations = remember { storageHelper.getAvailableLocations() }
    
    var currentPath by remember { mutableStateOf(startPath ?: defaultLocation.path)}
    var entries by remember { mutableStateOf(listOf<File>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedPath by remember { mutableStateOf(currentPath) }

    LaunchedEffect(currentPath) {
        error = null
        try {
            val dir = File(currentPath)
            if (dir.exists() && dir.isDirectory && dir.canRead()) {
                entries = dir.listFiles()
                    ?.filter { !it.name.startsWith(".") }
                    ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?: emptyList()
            } else {
                entries = emptyList()
                error = "Cannot access this folder"
            }
        } catch (e: Exception) {
            entries = emptyList()
            error = e.message ?: "Unknown error"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Select Folder", fontWeight = FontWeight.Bold)
                Text(
                    selectedPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableLocations.take(4).forEach { location ->
                        val isSelected = selectedPath.startsWith(location.path)
                        SuggestionChip(
                            onClick = { 
                                currentPath = location.path
                                selectedPath = location.path
                            },
                            label = { 
                                Text(
                                    text = location.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isSelected) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
                
                if (currentPath != selectedPath) {
                    TextButton(
                        onClick = { selectedPath = currentPath },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Use: $currentPath", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                
                if (error != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else if (entries.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.FolderOff,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Empty folder",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        val parentPath = File(currentPath).parent
                        if (parentPath != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            currentPath = parentPath
                                            selectedPath = parentPath
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        "Go up",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        "..",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                        
                        items(entries) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (file.isDirectory) {
                                            currentPath = file.absolutePath
                                            selectedPath = file.absolutePath
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    null,
                                    tint = if (file.isDirectory) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    Icon(
                                        Icons.Default.ChevronRight, 
                                        null, 
                                        modifier = Modifier.size(16.dp), 
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPathSelected(selectedPath) },
                enabled = File(selectedPath).canWrite() || File(selectedPath).exists()
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(16.dp)
    )
}
