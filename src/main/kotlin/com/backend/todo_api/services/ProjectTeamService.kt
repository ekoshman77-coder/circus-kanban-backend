package com.backend.todo_api.services

import com.backend.todo_api.data.entity.ProjectMemberEntity
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
import com.backend.todo_api.exceptions.UserDepartmentNotFoundException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
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
                projectRole = membership.role.name
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

        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val resultUsers = mutableSetOf<UserEntity>()

        for (context in userContexts) {
            // 🎯 Wir bauen die Resource dynamisch passend zum aktuellen Scope auf:
            val resourceToCheck = when (context.scope.name) {
                ScopeType.DEPARTMENT -> UserSecurityResource(
                    targetUserId = currentUserId,
                    departmentId = context.scopeInstanceId ?: requestingUser.departmentId
                )
                ScopeType.PROJECT -> UserSecurityResource(
                    targetUserId = currentUserId,
                    projectId = context.scopeInstanceId // 👈 WICHTIG: Das fehlte bisher!
                )
                else -> UserSecurityResource(
                    targetUserId = currentUserId,
                    departmentId = requestingUser.departmentId
                )
            }

            val hasAccess = permissionService.hasPermission(
                userContexts = listOf(context),
                action = ActionType.READ,
                resource = resourceToCheck
            )

            if (hasAccess) {
                when (context.scope.name) {
                    // 1. COMPANY (Admin): Darf absolut ALLE freigeschalteten User sehen
                    ScopeType.COMPANY -> {
                        resultUsers.addAll(userRepository.findByIsApprovedTrueAndIsArchivedFalse())
                    }

                    // 2. DEPARTMENT: Alle User aus der eigenen Abteilung hinzufügen
                    ScopeType.DEPARTMENT -> {
                        context.scopeInstanceId?.let { deptId ->
                            resultUsers.addAll(
                                userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, deptId)
                            )
                        }
                    }

                    // 3. PROJECT: Alle Kollegen aus Projekten hinzufügen, in denen der User Mitglied ist!
                    ScopeType.PROJECT -> {
                        context.scopeInstanceId?.let { projectId ->
                            val projectMembers = projectMemberRepository.findByProjectId(projectId)
                            resultUsers.addAll(
                                projectMembers.map { it.user }.filter { it.isApproved && !it.isArchived }
                            )
                        }
                    }

                    else -> {}
                }
            }
        }

        // Wandelt alle gesammelten (eindeutigen) User in DTOs um
        return resultUsers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            ProjectMemberDto(
                user = userService.entityToUserResponseDto(user, coffeeAccount),
                projectRole = RoleType.DEVELOPER
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

        val user = userRepository.findByIdAndIsArchivedFalse(userId)
            ?: throw UserNotFoundException("User existiert nicht")

        // 🛡️ BERECHTIGUNGSPRÜFUNG: Bearbeitungsrechte (WRITE) auf das Projekt reichen völlig aus!
        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val projectResource = project.toSecurityResource()

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE,
            resource = projectResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Hinzufügen von Teammitgliedern zum Projekt $projectId")
        }

        val existingMembership = projectMemberRepository.findByUserIdAndProjectId(userId, projectId)

        if (existingMembership != null) {
            existingMembership.role = role.toEntity(roleRepository)
            projectMemberRepository.save(existingMembership)
        } else {
            val newMembership = ProjectMemberEntity(project = project, user = user, role = role.toEntity(roleRepository))
            val savedMemberShip = projectMemberRepository.save(newMembership)
            project.teamMemberships.add(savedMemberShip)
            user.projectMemberships.add(savedMemberShip)
        }

        val coffeeAccount = coffeeAccountRepository.findById(userId).orElse(null)

        return ProjectMemberDto(
            user = userService.entityToUserResponseDto(user, coffeeAccount),
            projectRole = role
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
                projectRole = RoleType.DEVELOPER
            )
        }
    }
}