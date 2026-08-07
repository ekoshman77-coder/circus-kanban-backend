package com.backend.todo_api.services

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.model.RoleType

import com.backend.todo_api.model.ScopeType
import org.springframework.stereotype.Component

@Component
class UserContextResolver(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val departmentRepository: DepartmentRepository
) {
    fun resolveContexts(userId: String, ignoredRoleTypes: List<RoleType> = emptyList()): List<UserContext> {
        val user = userRepository.findById(userId).orElseThrow {
            UserDeletedException("User mit ID $userId existiert nicht.")
        }
        return this.resolveContexts(user, ignoredRoleTypes)
    }

    fun resolveContexts(user: UserEntity, ignoredRoleTypes: List<RoleType> = emptyList()): List<UserContext> {
        // 0. Sicherheits-Check: Nicht freigeschaltete User bekommen keinerlei Rechte
        if (!user.isApproved) {
            println("⚠️ [UserContextResolver] Zugriff verweigert: User ${user.id} (${user.username}) ist noch nicht geapprovt.")
            return emptyList()
        }

        val contexts = mutableListOf<UserContext>()

        // 1. Eigene Ressourcen-Ebene (RESOURCE)
        val ownerRole = roleRepository.findByName(RoleType.OWNER)

        if (ownerRole != null) {
            contexts.add(
                UserContext(
                    scope = ownerRole.scope,
                    scopeInstanceId = user.id,
                    role = ownerRole
                )
            )
        }

        // 2. Abteilungs-Ebene (DEPARTMENT / LOCATION / COMPANY)
        val deptId = user.departmentId
        val userRole = user.departmentRole

        if (deptId.isNullOrBlank() || userRole == null) {
            println("❌ [UserContextResolver] Kritischer Datenfehler: Geapprovter User ${user.id} hat keine Abteilung oder keine Abteilungsrolle!")
            throw IllegalStateException("Geapprovter Benutzer muss einer Abteilung und Abteilungsrolle zugewiesen sein.")
        }

        val department = departmentRepository.findById(deptId).orElseThrow {
            IllegalStateException("Abteilung mit ID $deptId für User ${user.id} existiert nicht in der Datenbank!")
        }

        val deptScope = department.defaultScope
        val instanceId = when (deptScope.name) {
            ScopeType.COMPANY -> null
            else -> department.id
        }

        contexts.add(
            UserContext(
                scope = deptScope,
                scopeInstanceId = instanceId,
                role = userRole
            )
        )

        // 3. Projekt-Ebene (PROJECT)
        user.projectMemberships.forEach { membership ->
            contexts.add(
                UserContext(
                    scope = membership.role.scope,
                    scopeInstanceId = membership.project.id,
                    role = membership.role
                )
            )
        }

        if (ignoredRoleTypes.isNotEmpty()) {
            return contexts.filterNot { context -> ignoredRoleTypes.contains(context.role.name) }
        }

        return contexts
    }
}