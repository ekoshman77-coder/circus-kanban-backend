package com.backend.todo_api.dto

open class CreateTodoDto(
    val task: String = "",
    val description: String? = null,
    val done: Boolean = false,
    val dueDate: Long = 0,
    val completedAt: Long? = null,
    val effort: Int = 0,
    val usedEffort: Int = 0,
    val createdAt: Long = 0,
    var userId: String = "",
    val category: String = "Allgemein",
    val milestoneId: String? = null,
    val assignedUserId: String? = null,
    val isStarted: Boolean = false,
    val teamStatus: String = "BACKLOG",
    val lastDeveloperId: String? = null,
    val reviewerId: String? = null,
    val reviewerUsedEffort: Double? = null
)

open class TodoDto(
    val id: String = "",

    // 🎯 JETZT MIT STANDARDWERTEN:
    task: String = "",
    description: String? = null,
    done: Boolean = false,
    dueDate: Long = 0,
    completedAt: Long? = null,
    effort: Int = 0,
    usedEffort: Int = 0,
    createdAt: Long = 0,
    userId: String = "",
    category: String = "Allgemein",
    milestoneId: String? = null,
    assignedUserId: String? = null,
    isStarted: Boolean = false,
    val effortChangesCount: Int? = 0,
    teamStatus: String = "BACKLOG",
    lastDeveloperId: String? = null,
    reviewerId: String? = null,
    reviewerUsedEffort: Double? = null
) : CreateTodoDto(
    task,
    description,
    done,
    dueDate,
    completedAt,
    effort,
    usedEffort,
    createdAt,
    userId,
    category,
    milestoneId,
    assignedUserId,
    isStarted,
    teamStatus,
    lastDeveloperId,
    reviewerId,
    reviewerUsedEffort
)