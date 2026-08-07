package com.backend.todo_api.model

import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.services.UserContext

data class DepartmentSecurityResource(
    val departmentId: String? = null
) : VisitableResource {

    override val resourceType = ResourceType.DEPARTMENT

    override fun matchesScope(context: UserContext): Boolean {
        return when (context.scope.name) {
            ScopeType.COMPANY -> true
            ScopeType.DEPARTMENT -> context.scopeInstanceId != null && context.scopeInstanceId == this.departmentId
            else -> false
        }
    }
}

// 🔄 Extension-Funktion für bereits geladene Entities
fun DepartmentEntity.toSecurityResource(): DepartmentSecurityResource {
    return DepartmentSecurityResource(departmentId = this.id)
}