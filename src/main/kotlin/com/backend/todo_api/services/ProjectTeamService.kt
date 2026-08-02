package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants.ADMIN_DEPARTMENT_NAME
import com.backend.todo_api.data.entity.ProjectMemberEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.CoffeeAccountRepository
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.dto.ProjectMemberDto
import com.backend.todo_api.dto.entityToUserResponseDto
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.exceptions.ProjectNotFoundException
import com.backend.todo_api.exceptions.TeamValidationException
import com.backend.todo_api.exceptions.UserDepartmentNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectTeamService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val coffeeAccountRepository: CoffeeAccountRepository,
    private val projectMemberRepository: ProjectMemberRepository,
    private val departmentRepository: DepartmentRepository
) {

    /**
     * 📥 Holt alle Teammitglieder eines Projekts inklusive ihrer echten Rolle
     */
    @Transactional(readOnly = true)
    fun getMembersForProject(projectId: String): List<ProjectMemberDto> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { throw ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }

        return project.teamMemberships.map { membership ->
            val user = membership.user
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)

            ProjectMemberDto(
                user = entityToUserResponseDto(user, coffeeAccount),
                projectRole = membership.role
            )
        }
    }

    /**
     * 🌍 Holt den Pool an auswählbaren Usern – strikt gefiltert nach der Abteilung des anfragenden Users!
     */
    @Transactional(readOnly = true)
    fun getAllGlobalUsersWithProjects(requestingUserId: String): List<ProjectMemberDto> {
        val requestingUser = userRepository.findById(requestingUserId)
            .orElseThrow { UserNotFoundException("User mit ID $requestingUserId nicht gefunden!") }

        val userDeptId = requestingUser.departmentId
        if (userDeptId.isNullOrBlank()) {
            return emptyList()
        }

        val userDepartment = departmentRepository.findById(userDeptId)
            .orElseThrow{ UserDepartmentNotFoundException("Benutzerbteilung ist veraltet") }

        // 🎯 FIX: Nutzt jetzt deine neue kombinierte Methode inklusive IsArchivedFalse-Schutz!
        val departmentUsers = userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, userDeptId)

        return departmentUsers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)

            ProjectMemberDto(
                user = entityToUserResponseDto(user, coffeeAccount),
            projectRole = "NONE"
            )
        }
    }

    /**
     * ➕ Weist einen User einem Projekt zu
     */
    @Transactional
    fun assignUserToProject(projectId: String, userId: String, role: String): ProjectMemberDto {
        val roleFromFrontend = role.trim()
        if (roleFromFrontend.isBlank()) {
            throw TeamValidationException("Es muss zwingend eine Projekt-Rolle übergeben werden!")
        }

        val existingMembership = projectMemberRepository.findByUserIdAndProjectId(userId, projectId)
        val finalUser: UserEntity

        if (existingMembership != null) {
            // Wenn er schon im Projekt ist, prüfen wir, ob er heimlich archiviert wurde
            if (existingMembership.user.isArchived) {
                throw UserNotFoundException("User existiert nicht")
            }
            existingMembership.role = roleFromFrontend
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

            val newMembership = ProjectMemberEntity(project = project, user = user, role = roleFromFrontend)
            projectMemberRepository.save(newMembership)
            finalUser = user
        }

        val coffeeAccount = coffeeAccountRepository.findById(finalUser.id).orElse(null)

        return ProjectMemberDto(
            user = entityToUserResponseDto(finalUser, coffeeAccount),
        projectRole = roleFromFrontend
        )
    }

    /**
     * 🗑️ Entfernt den User aus dem Projekt
     */
    @Transactional
    fun removeUserFromProject(projectId: String, memberId: String) {
        val project = projectRepository.findById(projectId)
            .orElseThrow { throw ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }
        val user = userRepository.findById(memberId)
            .orElseThrow { throw UserNotFoundException("User mit ID $memberId nicht gefunden!") }

        val membership = projectMemberRepository.findByUserIdAndProjectId(memberId, projectId)
        if (membership != null) {
            projectMemberRepository.delete(membership)
        }
    }

    /**
     * ☕ Aktualisiert das Kaffeekonto eines Users weltweit
     */
    @Transactional
    fun updateCoffeeAccount(userId: String, balance: Float, role: String, emoji: String): UserResponseDto {
        val account = coffeeAccountRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Konto nicht gefunden") }

        account.balance = balance
        account.role = role
        account.emoji = emoji
        coffeeAccountRepository.save(account)

        val user = userRepository.findById(userId).get()

        // 🛡️ Sicherheitswarnung, falls Admins das Kaffeekonto von Toten bearbeiten wollen
        if (user.isArchived) {
            throw UserNotFoundException("User existiert nicht")
        }

        return entityToUserResponseDto(user, account)
    }

    @Transactional(readOnly = true)
    fun getAllUsersForAdminBoard(): List<ProjectMemberDto> {
        // Holt alle registrierten und freigeschalteten Mitarbeiter der gesamten Firma
        val allApprovedUsers = userRepository.findByIsArchivedFalse()

        // Falls deine findByIsApproved Methode anders heißt, passe sie kurz an (z.B. findAll())
        return allApprovedUsers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            ProjectMemberDto(
                user = entityToUserResponseDto(user, coffeeAccount),
                projectRole = "NONE"
            )
        }
    }
}