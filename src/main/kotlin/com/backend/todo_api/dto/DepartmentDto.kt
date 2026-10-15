package com.backend.todo_api.dto

import com.backend.todo_api.model.DepartmentSpecializationType

data class DepartmentDto(
    val id: String,
    val name: String,
    val specialization: DepartmentSpecializationType? = null
)

data class CreateDepartmentDto(
    val name: String,
    val specialization: DepartmentSpecializationType? = null
)