package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ProjectMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ProjectMemberRepository : JpaRepository<ProjectMemberEntity, String> {
    // 🔍 Findet die Mitgliedschaft basierend auf User-ID und Projekt-ID
    fun findByUserIdAndProjectId(userId: String, projectId: String): ProjectMemberEntity?
    fun findByProjectId(projectId: String): List<ProjectMemberEntity>
    @Query("SELECT pm.project.id FROM ProjectMemberEntity pm WHERE pm.user.id = :userId")
    fun findProjectIdsByUserId(userId: String): List<String>
}