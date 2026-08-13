package com.backend.todo_api.dto

data class PlannerRecommendationsResponse(
    val roundId: String,
    val recommendations: List<RecommendedTodoResponse>
)
