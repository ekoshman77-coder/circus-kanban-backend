package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.RolePermissionRepository
import com.backend.todo_api.dto.MasterDataResponseDto
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
        resource: VisitableResource
    ): Boolean {
        val permissions = rolePermissionRepository.findByActionNameAndResourceName(
            action,
            resource.resourceType
        )

        for (userContext in userContexts) {
            val hasMatchingPermission = permissions.any { perm ->
                perm.role.id == userContext.role.id && perm.targetScope.name == userContext.scope.name
            }

            if (hasMatchingPermission && resource.matchesScope(userContext)) {
                return true
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

    fun getMasterData(): MasterDataResponseDto {
        return MasterDataResponseDto (
            departmentScopes = ScopeType.values().filter { it.isDepartmentSelectable }.map { it.name },
            projectRoles = RoleType.entries.filter { it.isProjectRole }.map { it.name },
            departmentRoles = RoleType.entries.filter { it.isDepartmentRole }.map { it.name }
        )
    }
}