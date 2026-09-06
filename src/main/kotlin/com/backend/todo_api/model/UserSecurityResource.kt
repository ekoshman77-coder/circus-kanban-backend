package com.backend.todo_api.model

data class UserSecurityResource(
    val targetUserId: String? = null,
    val departmentId: String? = null,
    val projectId: String? = null // Ermöglicht die Prüfung im PROJECT-Scope!
) : VisitableResource {
    override val resourceType: ResourceType = ResourceType.USER

    override fun matchesScope(userContext: UserContext): Boolean {
        return when (userContext.scope.name) {
            ScopeType.RESOURCE -> targetUserId == userContext.scopeInstanceId
            ScopeType.DEPARTMENT -> departmentId == userContext.scopeInstanceId
            ScopeType.PROJECT -> projectId == userContext.scopeInstanceId // Prüft Projekt-Zugehörigkeit!
            ScopeType.COMPANY -> true
        }
    }
}