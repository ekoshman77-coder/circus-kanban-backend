package com.backend.todo_api.dto

class UserResponseDto(
    id: String = "",
    username: String = "",
    firstName: String = "",
    lastName: String = "",
    departmentId: String? = null,
    isApproved: Boolean = false,
    var projectIds: List<String> = ArrayList(),
    var coffeeBalance: Float = 0f,
    var emoji: String = "",
    var role: String = ""
) : UserDto(id, username, firstName, lastName, "", departmentId, isApproved)