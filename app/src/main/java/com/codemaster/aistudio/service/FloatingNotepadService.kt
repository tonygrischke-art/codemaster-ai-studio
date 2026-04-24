package com.codemaster.aistudio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.codemaster.aistudio.MainActivity
import com.codemaster.aistudio.R

import java.io.File

class FloatingNotepadService : Service() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var isMinimized = false

    companion object {
        const val CHANNEL_ID = "FloatingNotepad"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Notepad",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps floating notepad running"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Notepad Active")
            .setContentText("Tap to return to app")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow() {
        try {
            val inflater = LayoutInflater.from(this)
            floatingView = inflater.inflate(R.layout.floating_notepad, null)

            params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 50
                y = 100
                width = 350
                height = 450
            }

            setupFloatingView()
            windowManager.addView(floatingView, params)

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to show notepad: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun setupFloatingView() {
        floatingView?.apply {
            findViewById<View>(R.id.header).setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params!!.x
                        initialY = params!!.y
                        touchX = event.rawX
                        touchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params!!.x = initialX + (event.rawX - touchX).toInt()
                        params!!.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                        true
                    }
                    else -> false
                }
            }

            findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
                saveNote()
                stopSelf()
            }

            findViewById<ImageButton>(R.id.btn_minimize).setOnClickListener {
                toggleMinimize()
            }

            findViewById<ImageButton>(R.id.btn_save).setOnClickListener {
                saveNote()
                Toast.makeText(this@FloatingNotepadService, "Saved", Toast.LENGTH_SHORT).show()
            }

            loadNote()
        }
    }

    private fun toggleMinimize() {
        floatingView?.apply {
            val content = findViewById<LinearLayout>(R.id.content)

            if (isMinimized) {
                content.visibility = View.VISIBLE
                params?.height = 450
            } else {
                content.visibility = View.GONE
                params?.height = 60
            }
            
            isMinimized = !isMinimized
            windowManager.updateViewLayout(this, params)
        }
    }

    private fun saveNote() {
        floatingView?.apply {
            val editText = findViewById<EditText>(R.id.note_editor)
            val note = editText.text.toString()
            val noteFile = File(filesDir, "floating_note.txt")
            noteFile.writeText(note)
        }
    }

    private fun loadNote() {
        floatingView?.apply {
            val editText = findViewById<EditText>(R.id.note_editor)
            val noteFile = File(filesDir, "floating_note.txt")
            if (noteFile.exists()) {
                editText.setText(noteFile.readText())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        saveNote()
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
