package com.backend.todo_api.dto

import jakarta.validation.constraints.NotBlank

data class AssignUserRequestDTO(
    @field:NotBlank(message = "Die User-ID darf nicht leer sein!")
    val userId: String
)
