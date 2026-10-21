package com.backend.todo_api.dto

data class TodoUpdateResponse (
    val todo: TodoDto,
    val gamificationResult: GamificationResult?,
    val streakInfo: StreakInfoDto? = null
)