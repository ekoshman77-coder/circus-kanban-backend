package com.backend.todo_api.dto

data class MasterDataResponseDto(
    val departmentScopes: List<String>,
    val departmentRoles: List<String>,
    val projectRoles: List<String>
)