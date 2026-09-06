package com.backend.todo_api.dto

import com.backend.todo_api.model.DepartmentSpecializationType
import com.backend.todo_api.model.ScopeType

data class DepartmentDto(
    val id: String,
    val name: String,
    val scope: ScopeType,
    val specialization: DepartmentSpecializationType? = null
)

data class CreateDepartmentDto(
    val name: String,
    val scope: ScopeType = ScopeType.DEPARTMENT,
    val specialization: DepartmentSpecializationType? = null
)