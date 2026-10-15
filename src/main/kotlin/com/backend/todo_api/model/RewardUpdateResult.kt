package com.backend.todo_api.model

import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.StreakInfoDto

data class RewardUpdateResult(
    val gamificationResult: GamificationResult,
    val streakInfo: StreakInfoDto
)