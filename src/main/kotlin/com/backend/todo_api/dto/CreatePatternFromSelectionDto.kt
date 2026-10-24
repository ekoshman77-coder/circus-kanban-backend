package com.backend.todo_api.dto

import com.backend.todo_api.model.PatternTypeEnum

data class CreatePatternFromSelectionDto(
    val selectedText: String,
    val patternType: PatternTypeEnum,
    val reason: String = "SENSITIVE_DATA", // z.B. PASSWORD, PIN, TOKEN
)