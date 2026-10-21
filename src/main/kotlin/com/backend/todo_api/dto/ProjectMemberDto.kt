package com.backend.todo_api.dto

import com.backend.todo_api.model.RoleType

data class ProjectMemberDto(
    val user: UserResponseDto,
    val projectRole: RoleType // Die spezifische Rolle im Projekt ('OWNER', 'VIEWER', etc.)
)