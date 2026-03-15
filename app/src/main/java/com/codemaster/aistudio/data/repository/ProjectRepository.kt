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
        val project = Project(
            name = name,
            language = language,
            description = description
        )
        return projectDao.insertProject(project)
    }

    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    suspend fun getProjectById(id: Long): Project? = projectDao.getProjectById(id)

    suspend fun updateProject(project: Project) = projectDao.updateProject(project)
}
