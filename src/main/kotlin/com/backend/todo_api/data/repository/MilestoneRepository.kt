package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.MilestoneEntity
import com.backend.todo_api.data.entity.TodoEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MilestoneRepository: JpaRepository<MilestoneEntity, String> {
    fun findByAssignedUserId(assignedUserId: String): List<MilestoneEntity>
    fun findByProjectIdIn(projectIds: List<String>): List<MilestoneEntity>
}