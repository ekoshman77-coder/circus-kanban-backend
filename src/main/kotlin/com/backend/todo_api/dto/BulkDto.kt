package com.backend.todo_api.dto

data class SyncResultDto(
    val liste: List<TodoDto>,
    val gamificationResult: GamificationResult
)