package com.codemaster.aistudio.ui.overlay

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: android.view.View
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
        showFloatingNotepad()
        isRunning = true
    }
    private fun showFloatingNotepad() {
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
            setContent { FloatingNotepadContent(windowManager, params, this) { stopSelf() } }
        }
        floatingView = composeView
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        windowManager.addView(floatingView, params)
    }
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
    override fun onBind(intent: Intent?): IBinder? = null
}

@androidx.compose.runtime.Composable
fun FloatingNotepadContent(windowManager: WindowManager, params: WindowManager.LayoutParams, view: android.view.View, onClose: () -> Unit) {
    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(true) }
    val bgColor = Color(0xFF1E1E2E)
    val accentColor = Color(0xFF7C3AED)
    val textColor = Color(0xFFE2E8F0)
    val borderColor = Color(0xFF374151)
    Box(modifier = Modifier.width(if (isExpanded) 280.dp else 48.dp).pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
            change.consume()
            params.x += dragAmount.x.toInt()
            params.y += dragAmount.y.toInt()
            windowManager.updateViewLayout(view, params)
        }
    }) {
        if (!isExpanded) {
            Box(modifier = Modifier.size(48.dp).background(accentColor, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                IconButton(onClick = { isExpanded = true }) { Text("📝", fontSize = 20.sp) }
            }
        } else {
            Column(modifier = Modifier.background(bgColor, RoundedCornerShape(12.dp)).border(1.dp, borderColor, RoundedCornerShape(12.dp)).padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📝 Notepad", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row {
                        IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(24.dp)) { Text("—", color = textColor, fontSize = 12.sp) }
                        IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) { Text("✕", color = Color(0xFFEF4444), fontSize = 12.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(Color(0xFF111827), RoundedCornerShape(8.dp)).border(1.dp, borderColor, RoundedCornerShape(8.dp)).padding(8.dp)) {
                    if (noteText.isEmpty()) Text("Paste errors, code snippets...", color = Color(0xFF6B7280), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    BasicTextField(value = noteText, onValueChange = { noteText = it }, textStyle = TextStyle(color = textColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; noteText = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: "" }, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(6.dp)) { Text("📋 Paste", fontSize = 10.sp, color = textColor) }
                    Button(onClick = { val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cb.setPrimaryClip(ClipData.newPlainText("note", noteText)); Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = accentColor), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(6.dp)) { Text("Copy", fontSize = 10.sp, color = Color.White) }
                    Button(onClick = { noteText = "" }, modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(6.dp)) { Text("Clear", fontSize = 10.sp, color = Color.White) }
                }
            }
        }
    }
}
