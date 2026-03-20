package com.codemaster.aistudio.data.repository

import com.codemaster.aistudio.data.dao.ProjectDao
import com.codemaster.aistudio.data.model.Project
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao
) {
    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()

    suspend fun createProject(name: String, language: String, description: String): Long {
        val project = Project(name = name, language = language, description = description)
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun getProjectById(id: Long): Project? = projectDao.getProjectById(id)

    suspend fun updateProject(project: Project) = projectDao.updateProject(project)

    /** Returns the real filesystem path for a project.
     *  Priority: project.path field > parsed from description > empty */
    suspend fun getProjectPath(id: Long): String {
        val project = projectDao.getProjectById(id) ?: return ""
        if (project.path.isNotBlank()) return project.path
        val desc = project.description
        return when {
            desc.startsWith("Loaded from: ")   -> desc.removePrefix("Loaded from: ")
            desc.startsWith("Imported from: ") -> desc.removePrefix("Imported from: ")
            else -> ""
        }
    }
}
