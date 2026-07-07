package com.backend.todo_api.dto

data class ProjectMemberDto(
    val user: UserResponseDto,
    val projectRole: String // Die spezifische Rolle im Projekt ('OWNER', 'VIEWER', etc.)
)