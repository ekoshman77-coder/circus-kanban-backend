package com.backend.todo_api.dto

/**
 * Allgemeines Feedback für ein einzelnes Todo.
 * @param todoId Die ID des Todos.
 * @param reason Der Grund für die Entscheidung (z. B. "too_heavy", "too_long" bei Rejection
 *               oder "motivation", "deadline", "energy" bei Acceptance).
 */
data class TodoFeedback(
    val todoId: String,
    val reason: String? = null
)
data class PlannerFeedbackRequest(
    val userId: String,
    val roundId: String,
    val acceptedTodoId: String? = null,
    val rejectedTodos: List<TodoFeedback> = emptyList()
)