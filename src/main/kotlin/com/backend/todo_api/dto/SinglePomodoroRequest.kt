package com.backend.todo_api.dto

// Für eine einzelne Online-Sitzung
data class SinglePomodoroRequest(
    val userId: String,
    val todoId: String,
    val count: Int = 1
)

