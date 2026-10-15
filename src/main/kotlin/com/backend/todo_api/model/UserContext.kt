package com.backend.todo_api.model

import com.backend.todo_api.data.entity.DepartmentSpecializationEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity

data class UserContext(
    val scope: ScopeEntity, // Bleibt saubere Entity!
    val scopeInstanceId: String? = null,
    val role: RoleEntity,
    val specialization: DepartmentSpecializationEntity? = null
)