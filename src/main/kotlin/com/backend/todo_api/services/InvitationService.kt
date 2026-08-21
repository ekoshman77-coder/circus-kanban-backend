package com.backend.todo_api.services

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.dto.SearchUserDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import org.springframework.stereotype.Service

@Service
class InvitationService(
    val userContextResolver: UserContextResolver,
    val permissionService: PermissionService,
    val userRepository: UserRepository,
    val departmentRepository: DepartmentRepository,
    val projectMemberRepository: ProjectMemberRepository

) {
    fun getAvailableDepartments(userId: String): List<DepartmentDto> {
        val contexts = userContextResolver.resolveContexts(userId)
        permissionService.getMaxAllowedUserContext(contexts, ActionType.INVITE, ResourceType.DEPARTMENT)
            ?: return emptyList()

        val departments = departmentRepository.findAll().map { it.toDto() }
        return departments
    }

    fun searchUsers(
        userId: String,
        departmentIds: List<String>,
        rolesFilter: List<RoleType>
    ): List<SearchUserDto> {

        val user = userRepository.findById(userId).orElseThrow {
            UserDeletedException("User mit ID $userId existiert nicht.")
        }
        if (departmentIds.size == 0) {
            return emptyList()
        }
        val userContexs = userContextResolver.resolveContexts(userId)

        val maxContext = permissionService.getMaxAllowedUserContext(
            userContexts = userContexs,
            action = ActionType.INVITE,
            resource = ResourceType.USER
        ) ?: return emptyList()

        val users = when (maxContext.scope.name) {
            ScopeType.COMPANY -> {
               if (rolesFilter.size == 0) userRepository.findByIsApprovedAndIsArchivedFalseAndDepartmentIdIn(true, departmentIds)
                else userRepository.findApprovedUsersInDepartmentsWithRoles(true, departmentIds, rolesFilter)
            }
            ScopeType.DEPARTMENT -> {
                if (!departmentIds.contains(user.departmentId)) {
                    return emptyList()
                }
                if (rolesFilter.size == 0) userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, user.departmentId)
                else userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalseAndDepartmentRole_NameIn(true, user.departmentId, rolesFilter)
            }
            ScopeType.PROJECT -> {
                val projectIds = projectMemberRepository.findProjectIdsByUserId(userId)
                if (projectIds.isEmpty()) return emptyList()

                // Hier nutzen wir unsere neue, schnelle Datenbank-Abfrage
                if (rolesFilter.size == 0) {
                    userRepository.findMembersByProjectIdsAndDepartmentIdIn(projectIds, departmentIds)
                } else {
                    userRepository.findMembersByProjectIdsAndDepartmentIdInWithRoleIn(projectIds, departmentIds, rolesFilter)
                }            }
            ScopeType.RESOURCE -> emptyList<UserEntity>()
        }
        val found = users.map { userEntity -> SearchUserDto(
            id = userEntity.id,
            firstName = userEntity.firstName,
            lastName = userEntity.lastName
        ) }
        return found
    }
}