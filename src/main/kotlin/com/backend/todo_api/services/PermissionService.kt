package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.ActionEntity
import com.backend.todo_api.data.entity.ResourceEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.RolePermissionRepository
import com.backend.todo_api.model.*

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class UserContext(
    val scope: ScopeEntity,
    val scopeInstanceId: String?,
    val role: RoleEntity
)

data class ResourceContext(
    val resource: ResourceType,
    val instanceId: String?
)

@Service
class PermissionService(
    private val rolePermissionRepository: RolePermissionRepository,
    private val departmentRepository: DepartmentRepository
) {

    private fun isUserAdmin(departmentId: String?): Boolean {
        if (departmentId == null) return false
        val adminDept = departmentRepository.findByNameIgnoreCase(AppConstants.ADMIN_DEPARTMENT_NAME)
        return adminDept?.id == departmentId
    }

    /**
     * Aufgabe A: Die Ja/Nein-Axt für Mutationen (CREATE, WRITE, DELETE, READ auf Einzelobjekte)
     */
    @Transactional(readOnly = true)
    fun hasPermission(
        userContexts: List<UserContext>,
        action: ActionType,
        resourceContext: ResourceContext
    ): Boolean {
        // Lädt alle Matrix-Regeln für diese Action und Resource
        val permissions = rolePermissionRepository.findByActionNameAndResourceName(
            action,
            resourceContext.resource
        )

        for (userContext in userContexts) {
            // Passt eine Regel in der Matrix zur Rolle und zum TargetScope des UserContexts?
            val hasMatchingPermission = permissions.any { perm ->
                perm.role.id == userContext.role.id && perm.targetScope.name == userContext.scope.name
            }

            if (hasMatchingPermission) {
                // Wenn der Context global/firmenweit ist (scopeInstanceId == null) -> Erlaubt!
                // Wenn der Context instanzgebunden ist -> IDs müssen übereinstimmen!
                if (userContext.scopeInstanceId == null || userContext.scopeInstanceId == resourceContext.instanceId) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Aufgabe B: Die Filter-Brille für Listen-Abfragen (Höchsten Scope ermitteln)
     */
    @Transactional(readOnly = true)
    fun getMaxAllowedUserContext(
        userContexts: List<UserContext>,
        action: ActionType,
        resource: ResourceType
    ): UserContext? {
        if (userContexts.isEmpty()) return null

        val roleNames = userContexts.map { it.role.name }

        val permissions = rolePermissionRepository.findByRoleNameInAndActionNameAndResourceName(
            roleNames,
            action,
            resource
        )

        // Findet den UserContext, der laut Matrix das höchste TargetScope-Level besitzt
        return userContexts
            // 1. Nur die Kontexte behalten, für die es in 'permissions' auch WIRKLICH Regeln gibt
            .filter { context -> permissions.any { it.role.name == context.role.name } }
            // 2. Jetzt aus diesen gültigen Kontexten denjenigen mit dem höchsten hierarchyLevel ziehen
            .maxByOrNull { context ->
                permissions
                    .filter { it.role.name == context.role.name }
                    .maxOf { it.targetScope.hierarchyLevel } // Hier reicht maxOf(), da der Filter nie leer ist!
            }
    }
}