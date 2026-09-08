package com.backend.todo_api.model

import com.backend.todo_api.data.entity.NoteEntity

data class NoteSecurityResource(
    val ownerUserId: String,
    val departmentId: String?,
    val projectId: String? = null // Zukünftig nutzbar!
) : VisitableResource {

    override val resourceType = ResourceType.NOTE

    override fun matchesScope(context: UserContext): Boolean {
        return when (context.scope.name) {
            ScopeType.COMPANY -> true
            ScopeType.DEPARTMENT -> context.scopeInstanceId != null && context.scopeInstanceId == this.departmentId
            ScopeType.RESOURCE -> context.scopeInstanceId == this.ownerUserId
            ScopeType.PROJECT -> context.scopeInstanceId != null && context.scopeInstanceId == this.projectId // aktuell false, da projectId null ist
            else -> false
        }
    }
}

// Note Mapper
fun NoteEntity.toSecurityResource(projectId: String? = null): NoteSecurityResource {
    return NoteSecurityResource(
        ownerUserId = this.userId,
        departmentId = this.departmentId,
        projectId = projectId
    )
}

