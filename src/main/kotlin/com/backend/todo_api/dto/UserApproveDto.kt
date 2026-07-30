package com.backend.todo_api.dto

import jakarta.validation.constraints.NotBlank

data class UserApproveDto(
    @field:NotBlank(message = "Department id darf nicht leer sein!")
    var departmentId: String = ""
)