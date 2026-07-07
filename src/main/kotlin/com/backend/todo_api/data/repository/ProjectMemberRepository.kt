package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ProjectMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ProjectMemberRepository : JpaRepository<ProjectMemberEntity, String> {
    // 🔍 Findet die Mitgliedschaft basierend auf User-ID und Projekt-ID
    fun findByUserIdAndProjectId(userId: String, projectId: String): ProjectMemberEntity?
    fun findByProjectId(projectId: String): List<ProjectMemberEntity>
}