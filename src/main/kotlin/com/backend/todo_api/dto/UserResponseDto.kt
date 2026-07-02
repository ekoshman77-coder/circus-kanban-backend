package com.backend.todo_api.dto

data class UserResponseDto(
    val id: String,
    val username: String,
    val firstName: String,
    val lastName: String,
    val projectIds: List<String>,
    val coffeeBalance: Float,
    val emoji: String,
    val role: String
)