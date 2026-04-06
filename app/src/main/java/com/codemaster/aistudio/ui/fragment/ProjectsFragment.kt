package com.codemaster.aistudio.ui.fragment

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.codemaster.aistudio.R
import java.io.File

class ProjectsFragment : Fragment() {

    private lateinit var rootLayout: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
            setPadding(32, 32, 32, 32)
        }
        loadProjects()
        return rootLayout
    }

    private fun loadProjects() {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val projectsDir = File(downloadDir, "projects")

        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }

        val title = TextView(requireContext()).apply {
            text = "Projects"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 0, 32)
        }
        rootLayout.addView(title)

        val info = TextView(requireContext()).apply {
            text = "Projects folder: ${projectsDir.absolutePath}\n\nBrowse your projects below:"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
            setPadding(0, 0, 0, 32)
        }
        rootLayout.addView(info)

        val files = projectsDir.listFiles() ?: emptyArray()
        if (files.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No projects found.\nCreate a new project or clone from Git."
                textSize = 14f
                setTextColor(android.graphics.Color.GRAY)
                setPadding(0, 32, 0, 32)
            }
            rootLayout.addView(empty)
        } else {
            files.forEach { file ->
                val item = TextView(requireContext()).apply {
                    text = if (file.isDirectory) "📁 ${file.name}/" else "📄 ${file.name}"
                    textSize = 16f
                    setTextColor(android.graphics.Color.WHITE)
                    setPadding(0, 16, 0, 16)
                    setOnClickListener {
                        Toast.makeText(context, "Selected: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                rootLayout.addView(item)
            }
        }
    }
}
