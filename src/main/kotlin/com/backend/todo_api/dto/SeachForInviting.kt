package com.backend.todo_api.dto

import com.backend.todo_api.model.RoleType

// Das, was wir zurückgeben (wie besprochen: nur das Nötigste!)
data class SearchUserDto(
    val id: String,
    val firstName: String,
    val lastName: String
)

data class InviteRequestDto(
    val departmentIds: List<String>,
    val departmentRoles: List<RoleType> = emptyList() // Optionaler Filter als Enum
)