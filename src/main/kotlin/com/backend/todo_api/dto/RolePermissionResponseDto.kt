package com.backend.todo_api.dto

data class RolePermissionResponseDto(
    val id: String,
    val role: String,
    val resource: String,
    val action: String,
    val targetScope: String,
    val specialization: String?
)