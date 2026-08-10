package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.dto.RecommendedTodoResponse
import org.springframework.stereotype.Service

@Service
class NeuralPlannerService: PlannerInterface {
    override fun processUserFeedback(
        userId: String,
        todoId: String,
        accepted: Boolean,
        rejectReason: String?,
        currentEnergy: String
    ) {
        TODO("Not yet implemented")
    }

    override fun calculatePerfectRecommendation(
        userId: String,
        userEnergy: String,
        workingTimeLeft: Double
    ): RecommendedTodoResponse {
        TODO("Not yet implemented")
    }

    override fun snoozeTodoInBackend(
        todoId: String,
        snoozeDurationInMinutes: Int
    ): TodoEntity? {
        TODO("Not yet implemented")
    }
}