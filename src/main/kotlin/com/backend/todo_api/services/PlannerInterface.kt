package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.dto.RecommendedTodoResponse

interface PlannerInterface {
    fun processUserFeedback(
        userId: String,
        todoId: String,
        accepted: Boolean,
        rejectReason: String?,
        currentEnergy: String
    )

    fun calculatePerfectRecommendation(userId: String, userEnergy: String, workingTimeLeft: Double): RecommendedTodoResponse

    fun snoozeTodoInBackend(todoId: String, snoozeDurationInMinutes: Int = 120): TodoEntity?
}