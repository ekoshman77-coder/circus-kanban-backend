package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ProjectEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProjectRepository : JpaRepository<ProjectEntity, String> {
    // 🔍 Findet alle Projekte, die zu einem bestimmten User gehören
    fun findByUserId(userId: String): List<ProjectEntity>
}