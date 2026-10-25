package com.backend.todo_api.dto

data class ProjectStreakInfoDto(
    val projectId: String,
    val streakDays: Int,
    val batteryPercentage: Int,
    val activeMembersCount: Int,
    val requiredMembersCount: Int,
    val todaysContributedMembers: Int,
    val todaysTotalEffort: Double,
    val activeShieldMembersCount: Int,
    val isShieldActive: Boolean
)