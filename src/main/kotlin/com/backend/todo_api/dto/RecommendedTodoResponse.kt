package com.backend.todo_api.dto

import com.backend.todo_api.model.PlannerType

// Detail-Objekt pro beteiligtem Planner
data class PlannerRecommendationDetail(
    val plannerType: PlannerType,
    val score: Double,
    val reasonCode: String
)

// Die Haupt-Response
data class RecommendedTodoResponse(
    val todo: TodoDto?,
    val plannerDetails: List<PlannerRecommendationDetail>,
    val modeCode: String = "STANDARD" // Z. B. "STANDARD" oder "ALL_SNOOZED"
)