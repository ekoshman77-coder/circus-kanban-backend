package com.backend.todo_api.dto

data class SyncResultDto(
    val liste: List<TodoDto>,
    val gamificationResult: GamificationResult
)

class TodoBulkDto(
    id: String = "",
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
    effortChangesCount: Int? = 0,
    teamStatus: String = "BACKLOG",
    lastDeveloperId: String? = null,

    // 🏷️ Das Zettelchen exklusiv hier!
    val syncAction: String = "FINE"
) : TodoDto(
    id, task, description, done, dueDate, completedAt, effort, usedEffort,
    createdAt, userId, category, milestoneId, assignedUserId, isStarted,
    effortChangesCount, teamStatus, lastDeveloperId
)