package com.backend.todo_api.model

interface ProjectStatsProjection {
    fun getTotalProjects(): Long
    fun getTotalMilestones(): Long
    fun getTotalTodos(): Long
}