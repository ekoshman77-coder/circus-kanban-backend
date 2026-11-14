package com.backend.todo_api.dto

import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.enums.TodoGroupChoice

data class PlannerRecommendationRequest(
    val userId: String,             // 🛡️ Lebenswichtig, damit jeder nur SEINE Todos sieht!
    val userEnergy: EnergyLevel,       // "low", "normal", "high"
    val workingTimeLeft: Long   // z.B. 4.5
)

data class PlannerGroupRecommendationRequest(
    val desiredGroup: TodoGroupChoice // BREAK, LIGHT oder HEAVY
)