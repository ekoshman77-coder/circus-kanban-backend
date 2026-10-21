package com.backend.todo_api.dto

data class AdminTodoFilterDto(
    val departmentId: String? = null,
    val projectId: String? = null,
    val isArchived: Boolean? = false,
    val fromDate: Long? = null,
    val toDate: Long? = null,
    val targetUserId: String? = null,
    val onlySensitive: Boolean = false
)