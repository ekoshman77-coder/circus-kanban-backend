package com.backend.todo_api.dto

data class MasterDataResponseDto(
    val departmentScopes: List<String>,
    val allScopes: List<String>,
    val departmentRoles: List<String>,
    val projectRoles: List<String>,
    val otherRoles: List<String>,
    val resources: List<String>,
    val actions: List<String>,
    val specializations: List<String>,
)