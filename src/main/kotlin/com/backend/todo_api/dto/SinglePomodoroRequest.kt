package com.backend.todo_api.dto

// Für eine einzelne Online-Sitzung
data class SinglePomodoroRequest(
    val userId: String,
    val todoId: String,
    val count: Int = 1
)

// Für den Offline-Sync (Bulk) aus dem Zug
data class OfflinePomodoroItem(
    val todoId: String,
    val timestamp: Long
)

data class BulkPomodoroRequest(
    val userId: String,
    val sessions: List<OfflinePomodoroItem>
)