package com.codemaster.aistudio.ui.projects

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codemaster.aistudio.R
import com.google.android.material.button.MaterialButton
import java.io.File

class ProjectsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var btnNewProject: MaterialButton
    private lateinit var btnImport: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_projects, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.recycler_projects)
        emptyView = view.findViewById(R.id.empty_view)
        btnNewProject = view.findViewById(R.id.btn_new_project)
        btnImport = view.findViewById(R.id.btn_import)
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        btnNewProject.setOnClickListener { createNewProject() }
        btnImport.setOnClickListener { importProject() }
        
        loadProjects()
    }

    private fun loadProjects() {
        val projectsDir = File(requireContext().filesDir, "projects")
        if (!projectsDir.exists()) projectsDir.mkdirs()
        
        val projects = projectsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        
        if (projects.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun createNewProject() {}

    private fun importProject() {}
}
