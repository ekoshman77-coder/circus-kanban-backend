package com.backend.todo_api.model

data class FeedbackForPlanner(
    val userId: String,
    val todoId: String,
    val userEnergy: EnergyLevel,
    val workingTimeLeft: Long,
    val accepted: Boolean,
    val rejectReason: String?,
    val score: Double,
    val timeUntilDue: Long,
    val effort: Int
)