package com.backend.todo_api.model

data class SensitiveMatch(
    val id: String? = null,
    val startIndex: Int,
    val endIndex: Int,
    val patternId: String,
    val reason: String,
    val weight: Double = 0.5,
    val isApproved: Boolean? = null
)