package com.backend.todo_api.dto

open class CreateNoteDto (
    val title: String = "",
    val content: String = "",
    val colorType: String = "",
    val tag: String? = null,
    val userId: String = "",
    val isInCalculation: Boolean = false,
    val temperature: Double? = null,
    val weatherCode: Int? = null
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
    weatherCode: Int? = null
): CreateNoteDto(title, content, colorType, tag, userId, isInCalculation, temperature, weatherCode)
