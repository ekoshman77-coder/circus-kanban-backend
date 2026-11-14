package com.backend.todo_api.model

import com.backend.todo_api.data.entity.RolePermissionEntity
import com.backend.todo_api.model.enums.DepartmentSpecializationType
import com.backend.todo_api.model.enums.ResourceType
import com.backend.todo_api.model.enums.ScopeType

data class AdminSecurityResource(
    val scope: ScopeType? = null
): VisitableResource {
    override val resourceType = ResourceType.PERMISSION

    override fun matchesScope(userContext: UserContext): Boolean {
        if (userContext.specialization == null) {
            return false
        }

        return userContext.specialization.name == DepartmentSpecializationType.ADMIN
    }
}

// Permission Mapper
fun RolePermissionEntity.toSecurityResource(): AdminSecurityResource {
    return AdminSecurityResource()
}
