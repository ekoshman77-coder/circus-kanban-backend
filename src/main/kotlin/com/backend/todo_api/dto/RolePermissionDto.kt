package com.backend.todo_api.dto

data class UpdateRolePermissionDto(
    val id: String,
    val targetScope: String
)

data class CreateRolePermissionDto(
    val role: String,
    val resource: String,
    val action: String,
    val targetScope: String
)