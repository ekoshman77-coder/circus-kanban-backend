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
}