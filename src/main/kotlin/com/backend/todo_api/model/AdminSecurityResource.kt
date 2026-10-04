package com.backend.todo_api.model

import com.backend.todo_api.data.entity.RolePermissionEntity

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
