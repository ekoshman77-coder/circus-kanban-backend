package com.backend.todo_api.model

import com.backend.todo_api.services.UserContext

data class TodoSecurityResource(
    val id: String?,                   // null bei neuem Todo
    val ownerUserId: String,           // userId des Erstellers
    val projectId: String? = null,     // Laufzeit-Wert (über Meilenstein aufgelöst)
    val departmentId: String? = null   // Laufzeit-Wert (über Projekt/User aufgelöst)
) : VisitableResource {

    override val resourceType = ResourceType.TODO

    override fun matchesScope(context: UserContext): Boolean {
        return when (context.scope.name) {
            ScopeType.COMPANY -> true
            ScopeType.DEPARTMENT -> context.scopeInstanceId != null && context.scopeInstanceId == this.departmentId
            ScopeType.PROJECT -> context.scopeInstanceId != null && context.scopeInstanceId == this.projectId
            ScopeType.RESOURCE -> context.scopeInstanceId != null && context.scopeInstanceId == this.ownerUserId
            else -> false
        }
    }
}