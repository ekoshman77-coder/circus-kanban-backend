package com.backend.todo_api.services

import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.dto.SearchUserDto
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.mapper.DepartmentMapper
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.DepartmentSecurityResource
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.UserSecurityResource
import org.springframework.stereotype.Service

@Service
class InvitationService(
    val userContextResolver: UserContextResolver,
    val permissionService: PermissionService,
    val userRepository: UserRepository,
    val departmentRepository: DepartmentRepository,
    val projectMemberRepository: ProjectMemberRepository,
    val departmentMapper: DepartmentMapper
) {

    /**
     * 🏢 Liefert auswählbare Abteilungen für Einladungen basierend auf den Rechte-Kontexten
     */
    fun getAvailableDepartments(userId: String): List<DepartmentDto> {
        val userContexts = userContextResolver.resolveContexts(userId)
        val resultDepartments = mutableSetOf<DepartmentEntity>()

        for (context in userContexts) {
            val dummyResource = DepartmentSecurityResource(departmentId = context.scopeInstanceId)

            val hasAccess = permissionService.hasPermission(
                userContexts = listOf(context),
                action = ActionType.INVITE,
                resource = dummyResource
            )

            if (hasAccess) {
                when (context.scope.name) {
                    // COMPANY (Admin): Darf in alle Abteilungen einladen
                    ScopeType.COMPANY -> {
                        resultDepartments.addAll(departmentRepository.findAll())
                    }
                    // DEPARTMENT: Darf in die eigene Abteilung einladen
                    ScopeType.DEPARTMENT -> {
                        context.scopeInstanceId?.let { deptId ->
                            departmentRepository.findById(deptId).ifPresent { resultDepartments.add(it) }
                        }
                    }
                    else -> {}
                }
            }
        }

        return resultDepartments.map { departmentMapper.toDto(it) }
    }

    /**
     * 🔍 Sucht nach Benutzern zum Einladen unter Berücksichtigung aller aktiven Scopes (Multi-Context)
     */
    fun searchUsers(
        userId: String,
        departmentIds: List<String>,
        rolesFilter: List<RoleType>
    ): List<SearchUserDto> {

        val user = userRepository.findById(userId).orElseThrow {
            UserDeletedException("User mit ID $userId existiert nicht.")
        }

        if (departmentIds.isEmpty()) {
            return emptyList()
        }

        val userContexts = userContextResolver.resolveContexts(userId)
        val resultUsers = mutableSetOf<UserEntity>()

        for (context in userContexts) {
            val dummyResource = UserSecurityResource(
                targetUserId = userId,
                departmentId = context.scopeInstanceId
            )

            val hasAccess = permissionService.hasPermission(
                userContexts = listOf(context),
                action = ActionType.INVITE,
                resource = dummyResource
            )

            if (hasAccess) {
                when (context.scope.name) {
                    // 1. COMPANY: Globale Suche über alle übergebenen Abteilungen
                    ScopeType.COMPANY -> {
                        val users = if (rolesFilter.isEmpty()) {
                            userRepository.findByIsApprovedAndIsArchivedFalseAndDepartmentIdIn(true, departmentIds)
                        } else {
                            userRepository.findApprovedUsersInDepartmentsWithRoles(true, departmentIds, rolesFilter)
                        }
                        resultUsers.addAll(users)
                    }

                    // 2. DEPARTMENT: Suche auf die eigene Abteilung beschränkt
                    ScopeType.DEPARTMENT -> {
                        user.departmentId?.let { userDeptId ->
                            if (departmentIds.contains(userDeptId)) {
                                val users = if (rolesFilter.isEmpty()) {
                                    userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, userDeptId)
                                } else {
                                    userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalseAndDepartmentRole_NameIn(true, userDeptId, rolesFilter)
                                }
                                resultUsers.addAll(users)
                            }
                        }
                    }

                    // 3. PROJECT: Suche innerhalb der eigenen Projekte
                    ScopeType.PROJECT -> {
                        val projectIds = projectMemberRepository.findProjectIdsByUserId(userId)
                        if (projectIds.isNotEmpty()) {
                            val users = if (rolesFilter.isEmpty()) {
                                userRepository.findMembersByProjectIdsAndDepartmentIdIn(projectIds, departmentIds)
                            } else {
                                userRepository.findMembersByProjectIdsAndDepartmentIdInWithRoleIn(projectIds, departmentIds, rolesFilter)
                            }
                            resultUsers.addAll(users)
                        }
                    }

                    ScopeType.RESOURCE -> {}
                }
            }
        }

        return resultUsers.map { userEntity ->
            SearchUserDto(
                id = userEntity.id,
                firstName = userEntity.firstName,
                lastName = userEntity.lastName
            )
        }
    }
}