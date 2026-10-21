package com.backend.todo_api.services

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.dto.RecommendedTodoResponse
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.FeedbackForPlanner
import com.backend.todo_api.model.PlannerRecomendation
import com.backend.todo_api.model.PlannerType

interface PlannerInterface {
    val plannerType: PlannerType

    fun processUserFeedback(feedback: FeedbackForPlanner)

    fun calculatePerfectRecommendation(
        userId: String,
        candidates: List<TodoEntity>,
        userEnergy: EnergyLevel,
        workingTimeLeft: Long): PlannerRecomendation?
}