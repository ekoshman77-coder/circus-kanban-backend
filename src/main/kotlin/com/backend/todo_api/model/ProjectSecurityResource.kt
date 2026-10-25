package com.backend.todo_api.model

import com.backend.todo_api.data.entity.ProjectEntity

data class ProjectSecurityResource(
    val id: String,
    val departmentId: String?,
    val memberUserIds: Set<String> = emptySet(),
    val ownerUserId: String? = null
) : VisitableResource {

    override val resourceType = ResourceType.PROJECT

    override fun matchesScope(context: UserContext): Boolean {
        return when (context.scope.name) {
            ScopeType.COMPANY -> true
            ScopeType.DEPARTMENT -> context.scopeInstanceId != null && context.scopeInstanceId == this.departmentId
            ScopeType.PROJECT -> context.scopeInstanceId == this.id || memberUserIds.contains(context.scopeInstanceId)
            ScopeType.RESOURCE -> context.scopeInstanceId != null && context.scopeInstanceId == this.ownerUserId
            else -> false
        }
    }
}

// Project Mapper
fun ProjectEntity.toSecurityResource(): ProjectSecurityResource {
    return ProjectSecurityResource(
        id = this.id,
        departmentId = this.departmentId,
        memberUserIds = this.teamMemberships.map { it.user.id }.toSet(),
        ownerUserId = this.userId
    )
}