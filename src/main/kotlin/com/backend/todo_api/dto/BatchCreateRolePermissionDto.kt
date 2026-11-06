package com.backend.todo_api.dto

import com.backend.todo_api.model.enums.ActionType
import com.backend.todo_api.model.enums.DepartmentSpecializationType
import com.backend.todo_api.model.enums.ResourceType
import com.backend.todo_api.model.enums.RoleType
import com.backend.todo_api.model.enums.ScopeType

data class BatchCreateRolePermissionDto(
    val resource: ResourceType,
    val scope: ScopeType,
    val roles: List<RoleType>,      // Multi-Select
    val actions: List<ActionType>,  // Multi-Select
    val specialization: DepartmentSpecializationType? = null
)