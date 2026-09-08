package com.backend.todo_api.dto

import com.backend.todo_api.model.RoleType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class UserApproveDto(
    @field:NotBlank(message = "Department id darf nicht leer sein!")
    var departmentId: String = "",
    @field:NotNull(message = "Department role darf nicht leer sein!")
    val departmentRole: RoleType
)