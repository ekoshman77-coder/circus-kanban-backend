package com.backend.todo_api.dto

import com.backend.todo_api.model.PatternTypeEnum

data class PredictReasonRequestDto(
    val selectedText: String,
    val patternType: PatternTypeEnum
)