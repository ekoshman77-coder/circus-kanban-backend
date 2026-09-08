package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.RolePermissionRepository
import com.backend.todo_api.model.*

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional


@Service
class PermissionService(
    private val rolePermissionRepository: RolePermissionRepository,
    private val departmentRepository: DepartmentRepository
) {

    fun hasPermission(
        userContexts: List<UserContext>,
        action: ActionType,
        resource: VisitableResource,
    ): Boolean {
        if (userContexts.isEmpty()) return false

        // 1. Rollen aus den übergebenen Kontexten extrahieren
        val userRoleNames = userContexts.map { it.role.name }

        // 2. Passende Berechtigungs-Regeln aus DB laden
        val matchingPermissions = rolePermissionRepository.findByRoleNameInAndActionNameAndResourceName(
            roleNames = userRoleNames,
            actionName = action,
            resourceName = resource.resourceType
        )

        if (matchingPermissions.isEmpty()) return false

        // 3. Evaluierung gegen die bereitgestellten Kontexte & VisitableResource-Scope
        return userContexts.any { context ->
            // A) Stimmt die Instanz/Scope-Ebene der konkreten Ressource überein?
            val scopeMatches = resource.matchesScope(context)

            if (!scopeMatches) return@any false

            // B) Passt dazu eine Berechtigung aus der Datenbank?
            matchingPermissions.any { permission ->
                val matchesRole = permission.role.id == context.role.id
                val matchesScope = permission.targetScope.id == context.scope.id

                // Spezialisierungs-Matching:
                val matchesSpecialization = when (val permSpec = permission.departmentSpecialization) {
                    null -> true
                    else -> permSpec.id == context.specialization?.id
                }

                matchesRole && matchesScope && matchesSpecialization
            }
        }
    }
}
