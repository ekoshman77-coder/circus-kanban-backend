package com.backend.todo_api.dto

data class ProjectDashboardStatsDTO(
    val totalProjects: Long,
    val totalMilestones: Long,
    val totalTodos: Long
)