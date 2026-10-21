package com.backend.todo_api.dto

data class SensitiveMatchDto(
    val id: String? = null,
    val startIndex: Int,
    val endIndex: Int,
    val patternId: String,
    val reason: String,
    val isApproved: Boolean? = null,
    val source: String = "SERVER_HEURISTIC"
)