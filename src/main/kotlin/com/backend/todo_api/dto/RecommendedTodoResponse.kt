package com.backend.todo_api.dto

import com.backend.todo_api.model.PlannerType

data class RecommendedTodoResponse(
    val todo: TodoDto?,
    val plannerType: PlannerType?,
    val modeCode: String,
    val reasonCode: String
)
