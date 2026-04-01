package com.codemaster.aistudio.ui.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class FloatingNotepadService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    private var windowManager: WindowManager? = null
    private var floatingView: android.view.View? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    companion object { var isRunning = false }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(1, createNotification())
        showFloatingNotepad()
        isRunning = true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("floating_notepad", "Floating Notepad", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Floating notepad overlay service"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, "floating_notepad")
            .setContentTitle("CodeMaster Notepad")
            .setContentText("Tap to open code editor")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setPriority(Notification.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingNotepad() {
        val wm = windowManager ?: return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 100; y = 200 }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingNotepadService)
            setViewTreeSavedStateRegistryOwner(this@FloatingNotepadService)
            setContent { FloatingNotepadContent(wm, params, this) { stopSelf() } }
        }

        floatingView = composeView
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        wm.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        floatingView?.let { view ->
            windowManager?.removeView(view)
        }
        floatingView = null
        windowManager = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingNotepadContent(windowManager: WindowManager, params: WindowManager.LayoutParams, view: android.view.View, onClose: () -> Unit) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(true) }
    var showLanguageMenu by remember { mutableStateOf(false) }
    var currentLanguage by remember { mutableStateOf("Kotlin") }
    var showActionsMenu by remember { mutableStateOf(false) }
    var windowWidth by remember { mutableStateOf(280) }
    var windowHeight by remember { mutableStateOf(200) }

    val bgColor = Color(0xFF1E1E2E)
    val accentColor = Color(0xFF7C3AED)
    val textColor = Color(0xFFE2E8F0)
    val borderColor = Color(0xFF374151)
    val successColor = Color(0xFF10B981)
    val warningColor = Color(0xFFF59E0B)

    val languages = listOf("Kotlin", "Java", "Python", "JavaScript", "TypeScript", "C", "C++", "C#", "Swift", "Dart", "PHP", "Ruby", "Go", "Rust", "Groovy", "XML", "JSON", "YAML", "HTML", "CSS", "SQL")

    Box(
        modifier = Modifier
            .width(if (isExpanded) windowWidth.dp else 48.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    params.x = (params.x + dragAmount.x.toInt()).coerceIn(0, 2000)
                    params.y = (params.y + dragAmount.y.toInt()).coerceIn(0, 3000)
                    windowManager.updateViewLayout(view, params)
                }
            }
    ) {
        if (!isExpanded) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { isExpanded = true }) {
                    Icon(Icons.Default.Edit, "Open", tint = Color.White)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .background(bgColor, RoundedCornerShape(12.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                // Header with drag handle and controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📝 Notepad",
                        color = textColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        // Language selector
                        Box {
                            TextButton(
                                onClick = { showLanguageMenu = true },
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(currentLanguage, color = accentColor, fontSize = 10.sp)
                                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(12.dp), tint = accentColor)
                            }
                            DropdownMenu(
                                expanded = showLanguageMenu,
                                onDismissRequest = { showLanguageMenu = false }
                            ) {
                                languages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang, fontSize = 12.sp) },
                                        onClick = {
                                            currentLanguage = lang
                                            showLanguageMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(24.dp)) {
                            Text("—", color = textColor, fontSize = 12.sp)
                        }
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                            Text("✕", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Code editor area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(windowHeight.dp)
                        .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    if (noteText.isEmpty()) {
                        Text(
                            "Paste code here...\nPython, React, Java, C, C++, Kotlin, Groovy, Gradle, Firebase",
                            color = Color(0xFF6B7280),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    BasicTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        textStyle = TextStyle(
                            color = textColor,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ActionChip(
                        icon = Icons.Default.ContentPaste,
                        label = "Paste",
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            noteText = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ActionChip(
                        icon = Icons.Default.ContentCopy,
                        label = "Copy",
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("code", noteText))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ActionChip(
                        icon = Icons.Default.Clear,
                        label = "Clear",
                        onClick = { noteText = "" },
                        modifier = Modifier.weight(1f),
                        containerColor = Color(0xFF7F1D1D)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Quick actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ActionChip(
                        icon = Icons.Default.Edit,
                        label = "Edit",
                        onClick = {
                            if (noteText.isNotBlank()) {
                                Toast.makeText(context, "Opening AI editor...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    ActionChip(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Fix",
                        onClick = {
                            if (noteText.isNotBlank()) {
                                Toast.makeText(context, "Fixing code...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = successColor.copy(alpha = 0.3f)
                    )
                    ActionChip(
                        icon = Icons.Default.Build,
                        label = "Build",
                        onClick = {
                            if (noteText.isNotBlank()) {
                                Toast.makeText(context, "Building code...", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        containerColor = warningColor.copy(alpha = 0.3f)
                    )
                }

                // Resize handle at bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val startY = it
                                    try {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val currentY = event.changes.first().position.y
                                                val delta = (currentY - startY).toInt()
                                                if (delta != 0) {
                                                    windowHeight = (windowHeight + delta / 10).coerceIn(100, 500)
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(borderColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFF374151)
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        shape = RoundedCornerShape(6.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = Color.White)
        Spacer(Modifier.width(2.dp))
        Text(label, fontSize = 10.sp, color = Color.White)
    }
}