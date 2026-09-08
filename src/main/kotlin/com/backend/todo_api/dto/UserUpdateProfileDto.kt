package com.backend.todo_api.dto

import jakarta.validation.constraints.NotBlank

data class UserUpdateProfileDto(
    @field:NotBlank(message = "Der Vorname darf nicht leer sein!")
    val firstName: String = "",

    @field:NotBlank(message = "Der Nachname darf nicht leer sein!")
    val lastName: String = ""
)