package com.backend.todo_api.dto

import com.backend.todo_api.model.EnergyLevel

data class SlotRecommendationDto(
    val todoId: String,
    val plannerDetails: List<PlannerRecommendationDetail>
)

data class BlueprintSlotDto(
    val startTime: String,
    val endTime: String,
    val energyLevel: EnergyLevel,
    val recommendations: List<SlotRecommendationDto>, // 👈 Liste statt Einzel-ID
    val reason: String
)

data class DailyBlueprintResponse(
    val roundId: String,
    val userId: String,
    val generatedAtHour: Int,
    val slots: List<BlueprintSlotDto>
)