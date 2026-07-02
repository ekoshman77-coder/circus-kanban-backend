package com.backend.todo_api.dto

data class PlannerRecommendationRequest(
    val userId: String,             // 🛡️ Lebenswichtig, damit jeder nur SEINE Todos sieht!
    val userEnergy: String,       // "low", "normal", "high"
    val workingTimeLeft: Double   // z.B. 4.5
)