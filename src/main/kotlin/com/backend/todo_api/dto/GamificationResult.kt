package com.backend.todo_api.dto

data class GamificationResult(
    val levelUp: Boolean,
    val currentLevel: Int,
    val levelTitle: String,
    val levelIcon: String,
    val currentXp: Int,
    val currentLevelXpStart: Int,
    val nextLevelXpRequired: Int
)