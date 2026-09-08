package com.backend.todo_api.dto

data class RejectedTodoFeedback(
    val todoId: String,
    val rejectReason: String? = null // z. B. "too_heavy", "too_long", "no_motivation"
)

data class PlannerFeedbackRequest(
    val userId: String,
    val roundId: String,
    val acceptedTodoId: String? = null,
    val rejectedTodos: List<RejectedTodoFeedback> = emptyList()
)