package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants.ADMIN_DEPARTMENT_NAME
import com.backend.todo_api.data.entity.ProjectMemberEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.CoffeeAccountRepository
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.dto.ProjectMemberDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.exceptions.ProjectNotFoundException
import com.backend.todo_api.exceptions.TeamValidationException
import com.backend.todo_api.exceptions.UserDeletedException
import com.backend.todo_api.exceptions.UserDepartmentNotFoundException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ProjectSecurityResource
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.UserSecurityResource
import com.backend.todo_api.model.toEntity
import com.backend.todo_api.model.toSecurityResource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectTeamService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val coffeeAccountRepository: CoffeeAccountRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val departmentRepository: DepartmentRepository,
    private val roleRepository: RoleRepository,
    private val userContextResolver: UserContextResolver,
    private val permissionService: PermissionService,
    private val userService: UserService
) {

    /**
     * 📥 Holt alle Teammitglieder eines Projekts inklusive ihrer echten Rolle
     */
    @Transactional(readOnly = true)
    fun getMembersForProject(projectId: String, currentUserId: String): List<ProjectMemberDto> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }

        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val projectResource = project.toSecurityResource()

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = projectResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Einsehen des Teams für Projekt $projectId")
        }

        return project.teamMemberships.map { membership ->
            val user = membership.user
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)

            ProjectMemberDto(
                user = userService.entityToUserResponseDto(user, coffeeAccount),
                projectRole = membership.role.name?.name ?: "NONE"
            )
        }
    }

    /**
     * 🌍 Holt den Pool an auswählbaren Usern – strikt gefiltert nach der Abteilung des anfragenden Users!
     */
    @Transactional(readOnly = true)
    fun getAllGlobalUsersWithProjects(currentUserId: String): List<ProjectMemberDto> {
        val requestingUser = userRepository.findById(currentUserId)
            .orElseThrow { UserNotFoundException("User mit ID $currentUserId nicht gefunden!") }

        val userDeptId = requestingUser.departmentId
        if (userDeptId.isNullOrBlank()) {
            return emptyList()
        }

        departmentRepository.findById(userDeptId)
            .orElseThrow { UserDepartmentNotFoundException("Benutzerabteilung ist veraltet") }

        // 🛡️ BERECHTIGUNGSPRÜFUNG: Darf der User die Benutzer-Ressource in seiner Abteilung lesen?
        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val userResource = UserSecurityResource(targetUserId = currentUserId, departmentId = userDeptId)

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = userResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Abrufen der Abteilungsmitglieder")
        }

        val departmentUsers = userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, userDeptId)

        return departmentUsers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)

            ProjectMemberDto(
                user = userService.entityToUserResponseDto(user, coffeeAccount),
                projectRole = "NONE"
            )
        }
    }

    /**
     * ➕ Weist einen User einem Projekt zu
     */
    @Transactional
    fun assignUserToProject(currentUserId: String, projectId: String, userId: String, role: RoleType): ProjectMemberDto {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }

        // 🛡️ BERECHTIGUNGSPRÜFUNG: Bearbeitungsrechte (WRITE) auf das Projekt reichen völlig aus!
        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val projectResource = project.toSecurityResource()

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE, // 👈 Hier WRITE / UPDATE statt DELETE
            resource = projectResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Hinzufügen von Teammitgliedern zum Projekt $projectId")
        }

        val existingMembership = projectMemberRepository.findByUserIdAndProjectId(userId, projectId)
        val finalUser: UserEntity

        if (existingMembership != null) {
            // Wenn er schon im Projekt ist, prüfen wir, ob er heimlich archiviert wurde
            if (existingMembership.user.isArchived) {
                throw UserNotFoundException("User existiert nicht")
            }

            existingMembership.role = RoleEntity()
            projectMemberRepository.save(existingMembership)
            finalUser = existingMembership.user
        } else {
            val project = projectRepository.findById(projectId)
                .orElseThrow { ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }
            val user = userRepository.findById(userId)
                .orElseThrow { UserNotFoundException("User mit ID $userId nicht gefunden!") }

            // 🛡️ SICHERHEITS-CHECK: Verhindert, dass archivierte User neuen Projekten hinzugefügt werden
            if (user.isArchived) {
                throw UserNotFoundException("User existiert nicht")
            }

            val newMembership = ProjectMemberEntity(project = project, user = user, role = role.toEntity(roleRepository))
            projectMemberRepository.save(newMembership)
            finalUser = user
        }

        val coffeeAccount = coffeeAccountRepository.findById(finalUser.id).orElse(null)

        return ProjectMemberDto(
            user = userService.entityToUserResponseDto(finalUser, coffeeAccount),
            projectRole = role.name
        )
    }

    /**
     * 🗑️ Entfernt den User aus dem Projekt
     */
    @Transactional
    fun removeUserFromProject(projectId: String, targetUserId: String, currentUserId: String) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }

        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val projectResource = project.toSecurityResource()

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE,
            resource = projectResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Entfernen von Teammitgliedern aus Projekt $projectId")
        }

        val membership = projectMemberRepository.findByUserIdAndProjectId(targetUserId, projectId)
        if (membership != null) {
            project.teamMemberships.remove(membership)
            membership.user.projectMemberships.remove(membership)
            projectMemberRepository.delete(membership)
        }
    }

    /**
     * ☕ Aktualisiert das Kaffeekonto eines Users weltweit
     */
    @Transactional
    fun updateCoffeeAccount(
        currentUserId: String,
        targetUserId: String,
        balance: Float,
        role: String,
        emoji: String
    ): UserResponseDto {

        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val targetUser = userRepository.findById(targetUserId)
            .orElseThrow { UserNotFoundException("User mit ID $targetUserId nicht gefunden!") }

        val userResource = UserSecurityResource(targetUserId = targetUserId, departmentId = targetUser.departmentId)

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE,
            resource = userResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Ändern dieses Kaffeekontos")
        }

        if (targetUser.isArchived) {
            throw UserNotFoundException("User existiert nicht oder ist archiviert.")
        }

        val account = coffeeAccountRepository.findById(targetUserId)
            .orElseThrow { IllegalArgumentException("Konto nicht gefunden") }

        account.balance = balance
        account.role = role
        account.emoji = emoji
        coffeeAccountRepository.save(account)

        return userService.entityToUserResponseDto(targetUser, account)
    }

    @Transactional(readOnly = true)
    fun getAllUsersForAdminBoard(currentUserId: String): List<ProjectMemberDto> {
        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val userResource = UserSecurityResource(targetUserId = currentUserId, departmentId = null)

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = userResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Admin-Berechtigung für das globale User-Board")
        }

        val allApprovedUsers = userRepository.findByIsArchivedFalse()

        return allApprovedUsers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            ProjectMemberDto(
                user = userService.entityToUserResponseDto(user, coffeeAccount),
                projectRole = "NONE"
            )
        }
    }
}