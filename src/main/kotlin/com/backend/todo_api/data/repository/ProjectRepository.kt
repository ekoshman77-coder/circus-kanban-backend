package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.model.ProjectStatsProjection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ProjectRepository : JpaRepository<ProjectEntity, String> {
    // 🔍 Findet alle Projekte, die zu einem bestimmten User gehören
    fun findByUserId(userId: String): List<ProjectEntity>

    // 🌟 NEU: Holt alle aktiven Projekte einer bestimmten Abteilung (Status ungleich 'Zip')
    fun findByDepartmentIdAndStatusNot(departmentId: String, status: String = "Zip"): List<ProjectEntity>

    // 🌟 NEU: Für die Geschäftsleitung (Direction) – sieht alle aktiven Projekte weltweit
    fun findByStatusNot(status: String = "Zip"): List<ProjectEntity>

    @Query("""
        SELECT 
            COUNT(DISTINCT p.id) AS totalProjects,
            COUNT(DISTINCT m.id) AS totalMilestones,
            COUNT(DISTINCT t.id) AS totalTodos
        FROM ProjectEntity p
        LEFT JOIN p.milestones m
        LEFT JOIN p.teamMemberships mem
        LEFT JOIN TodoEntity t ON t.milestoneId = m.id
        WHERE p.userId = :userId OR mem.user.id = :userId
        AND p.status != 'Zip'
    """)
    fun getDashboardStatistics(@Param("userId") userId: String): ProjectStatsProjection
}