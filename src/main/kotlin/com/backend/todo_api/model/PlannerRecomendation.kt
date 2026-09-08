package com.backend.todo_api.model

data class PlannerRecomendation (
        val plannerType: PlannerType,
        val todoId: String,
        val score: Double,
        val energyLevel: EnergyLevel,
        val timeUntilDue: Long,
        val workingTimeLeft: Long,
        val effort: Int,
        val reason: String
)