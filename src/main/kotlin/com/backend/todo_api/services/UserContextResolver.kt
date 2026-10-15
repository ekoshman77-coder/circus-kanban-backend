package com.backend.todo_api.services

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.UserContext
import com.backend.todo_api.model.toEntity
import org.springframework.stereotype.Component

@Component
class UserContextResolver(
    private val roleRepository: RoleRepository,
    private val scopeRepository: ScopeRepository,
    private val departmentRepository: DepartmentRepository,
    private val userRepository: UserRepository
) {
    fun resolveContexts(
        userId: String,
        ignoredRoleTypes: List<RoleType> = emptyList(),
        ignoreDepartmentSpecialization: Boolean = false
    ): List<UserContext> {
        val user = userRepository.findById(userId).orElseThrow {
            UserDeletedException("User mit ID $userId existiert nicht.")
        }
        return this.resolveContexts(user, ignoredRoleTypes, ignoreDepartmentSpecialization)
    }

    fun resolveContexts(
        user: UserEntity,
        ignoredRoleTypes: List<RoleType> = emptyList(),
        ignoreDepartmentSpecialization: Boolean = false
    ): List<UserContext> {
        if (!user.isApproved) {
            return emptyList()
        }

        val contexts = mutableListOf<UserContext>()

        // 1. Eigene Ressourcen-Ebene (RESOURCE / OWNER)
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

        // 2. Abteilungs-Ebene
        val deptId = user.departmentId
        val userRole = user.departmentRole

        if (!deptId.isNullOrBlank() && userRole != null) {
            val department = departmentRepository.findById(deptId).orElseThrow {
                IllegalStateException("Abteilung mit ID $deptId existiert nicht!")
            }

            // A) Standard-Abteilungs-Kontext (Standard-Board)
            contexts.add(
                UserContext(
                    scope = ScopeType.DEPARTMENT.toEntity(scopeRepository),
                    scopeInstanceId = department.id,
                    role = userRole
                )
            )

            // B) Spezialisierungs-Kontext (Globales Admin-/Special-Board)
            if (!ignoreDepartmentSpecialization && department.specialization != null) {
                val companyScope = ScopeType.COMPANY.toEntity(scopeRepository)

                contexts.add(
                    UserContext(
                        scope = companyScope,
                        scopeInstanceId = null,
                        role = userRole,
                        specialization = department.specialization
                    )
                )
            }
        }

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

        // Filterung über ignoredRoleTypes
        if (ignoredRoleTypes.isNotEmpty()) {
            return contexts.filterNot { context -> ignoredRoleTypes.contains(context.role.name) }
        }

        return contexts
    }
}