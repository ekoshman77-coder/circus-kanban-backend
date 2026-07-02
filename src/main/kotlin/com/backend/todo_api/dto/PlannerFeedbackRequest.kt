package com.backend.todo_api.dto

data class PlannerFeedbackRequest(
    val userId: String,
    val todoId: String,
    val accepted: Boolean,
    val rejectReason: String?, // "no_motivation", "too_heavy", "too_long" oder null
    val currentEnergy: String  // "low", "medium", "high" -> Wichtig fürs biologische Lernen!
)