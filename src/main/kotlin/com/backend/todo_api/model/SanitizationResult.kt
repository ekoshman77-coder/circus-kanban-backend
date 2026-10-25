package com.backend.todo_api.model

data class SanitizationResult(
    val matches: List<SensitiveMatch>,
    val found: Boolean
)
