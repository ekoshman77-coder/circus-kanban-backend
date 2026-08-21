package com.backend.todo_api.dto

import com.backend.todo_api.model.RoleType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class AssignUserRequestDTO(
    @field:NotBlank(message = "Die User-ID darf nicht leer sein!")
    val userId: String,
    @field:NotBlank(message = "Die Projekt-ID darf nicht leer sein!")
    val projectId: String,
    @field:NotNull(message = "Die Projektrole darf nicht leer sein!")
    val projectRole: RoleType
)
