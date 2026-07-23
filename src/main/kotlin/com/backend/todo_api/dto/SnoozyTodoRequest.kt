package com.backend.todo_api.dto

import jakarta.validation.constraints.NotBlank

data class SnoozyTodoRequest(
    @field:NotBlank(message = "TodoId darf nicht leer sein!")
    var todoId: String,
    var durationInMin: Int
)
