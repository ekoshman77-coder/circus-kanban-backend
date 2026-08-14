package com.backend.todo_api.dto

import com.backend.todo_api.model.EnergyLevel

data class PlannerRecommendationRequest(
    val userId: String,             // 🛡️ Lebenswichtig, damit jeder nur SEINE Todos sieht!
    val userEnergy: EnergyLevel,       // "low", "normal", "high"
    val workingTimeLeft: Long   // z.B. 4.5
)