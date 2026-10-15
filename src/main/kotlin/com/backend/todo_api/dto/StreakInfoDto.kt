package com.backend.todo_api.dto

data class StreakInfoDto(
    val streakDays: Int,
    val batteryPercentage: Int,
    val pufferDaysRemaining: Double,
    val isShieldActive: Boolean,
    val infoText: String
)