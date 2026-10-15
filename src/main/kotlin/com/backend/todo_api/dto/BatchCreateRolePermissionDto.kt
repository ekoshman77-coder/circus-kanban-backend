package com.backend.todo_api.dto

import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.DepartmentSpecializationType
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType

data class BatchCreateRolePermissionDto(
    val resource: ResourceType,
    val scope: ScopeType,
    val roles: List<RoleType>,      // Multi-Select
    val actions: List<ActionType>,  // Multi-Select
    val specialization: DepartmentSpecializationType? = null
)