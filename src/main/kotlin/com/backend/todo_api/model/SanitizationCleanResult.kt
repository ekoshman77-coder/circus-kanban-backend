package com.backend.todo_api.model

data class SanitizationCleanResult(
    val cleanedText: String,
    val isSensitive: Boolean,
    val matches: List<SensitiveMatch>
)