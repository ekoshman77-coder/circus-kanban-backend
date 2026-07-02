package com.backend.todo_api.dto

data class RecommendedTodoResponse(
    val todo: TodoDto?,
    val modeCode: String,     // "STANDARD", "RECHERCHE", "CLEAN_SLATE"
    val reasonCode: String    // "DEFAULT", "LOW_ENERGY_SHORT_TIME", "NO_TODOS_LEFT"
)

