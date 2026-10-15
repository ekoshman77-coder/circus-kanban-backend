package com.backend.todo_api.dto

import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.toEntity

open class CreateNoteDto (
    val title: String = "",
    val content: String = "",
    val colorType: String = "",
    val tag: String? = null,
    val userId: String = "",
    val isInCalculation: Boolean = false,
    val temperature: Double? = null,
    val weatherCode: Int? = null,
    val departmentId: String? = null,
    val scope: ScopeType = ScopeType.DEPARTMENT
)

class NoteDto (
    val id: String = "",
    title: String = "",
    content: String = "",
    colorType: String = "",
    tag: String? = null,
    userId: String = "",
    isInCalculation: Boolean = false,
    temperature: Double? = null,
    weatherCode: Int? = null,
    departmentId: String? = null,
    scope: ScopeType
): CreateNoteDto(title, content, colorType, tag, userId, isInCalculation, temperature, weatherCode, departmentId, scope)
