package com.backend.todo_api.dto

data class NoteStatusChangeRequest(
    val id: String,
    val inCalculation: Boolean
)