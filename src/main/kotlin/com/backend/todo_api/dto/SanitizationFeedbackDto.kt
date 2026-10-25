package com.backend.todo_api.dto

data class SanitizationFeedbackItemDto(
    val match: SensitiveMatchDto,
    val feedback: Boolean
)

data class SanitizationFeedbackDto(
    val feedbacks: List<SanitizationFeedbackItemDto>,
    val todoId: String
)