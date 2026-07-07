package com.backend.todo_api.services

import com.backend.todo_api.data.entity.ProjectMemberEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.CoffeeAccountRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.dto.ProjectMemberDto // 🚀 UNSER NEUES DTO HIER REIN
import com.backend.todo_api.exceptions.ProjectNotFoundException
import com.backend.todo_api.exceptions.TeamValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectTeamService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val coffeeAccountRepository: CoffeeAccountRepository,
    private val projectMemberRepository: ProjectMemberRepository
) {

    /**
     * 📥 Holt alle Teammitglieder eines Projekts inklusive ihrer echten Rolle aus der Zwischentabelle
     */
    @Transactional(readOnly = true)
    fun getMembersForProject(projectId: String): List<ProjectMemberDto> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { throw ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }

        return project.teamMemberships.map { membership ->
            val user = membership.user
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)

            val userDto = UserResponseDto(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
                coffeeBalance = coffeeAccount?.balance ?: 0f,
                role = coffeeAccount?.role ?: "",
                emoji = coffeeAccount?.emoji ?: "",
                projectIds = user.projectMemberships.map { it.project.id }
            )

            // 🌟 Wir verheiraten das UserResponseDto mit der echten projectRole aus der DB-Entity!
            ProjectMemberDto(
                user = userDto,
                projectRole = membership.role // OWNER, DEVELOPER, etc.
            )
        }
    }

    /**
     * 🌍 Holt alle User des Systems (Wartebank) und verpasst ihnen im DTO die Rolle "NONE"
     */
    @Transactional(readOnly = true)
    fun getAllGlobalUsersWithProjects(): List<ProjectMemberDto> {
        val allUsers = userRepository.findAll()

        return allUsers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)

            val userDto = UserResponseDto(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
                coffeeBalance = coffeeAccount?.balance ?: 0f,
                role = coffeeAccount?.role ?: "",
                emoji = coffeeAccount?.emoji ?: "",
                projectIds = user.projectMemberships.map { it.project.id }
            )

            // 🌍 Weil sie auf der globalen Wartebank sitzen, ist die Projekt-Rolle hier künstlich "NONE"
            ProjectMemberDto(
                user = userDto,
                projectRole = "NONE"
            )
        }
    }

    /**
     * ➕ Weist einen User einem Projekt mit einer spezifischen Rolle zu
     */
    @Transactional
    fun assignUserToProject(projectId: String, userId: String, role: String): ProjectMemberDto {
        val roleFromFrontend = role.trim()
        if (roleFromFrontend.isBlank()) {
            throw TeamValidationException("Es muss zwingend eine Projekt-Rolle übergeben werden!")
        }

        val existingMembership = projectMemberRepository.findByUserIdAndProjectId(userId, projectId)

        // 💡 Wir deklarieren eine Variable für den User, den wir am Ende fürs DTO brauchen
        val finalUser: UserEntity

        if (existingMembership != null) {
            existingMembership.role = roleFromFrontend
            projectMemberRepository.save(existingMembership)
            finalUser = existingMembership.user // Hier haben wir den User direkt!
        } else {
            // Hier laden wir project und user das EINZIGE Mal aus den Repositories
            val project = projectRepository.findById(projectId)
                .orElseThrow { ProjectNotFoundException("Projekt mit ID $projectId nicht gefunden!") }
            val user = userRepository.findById(userId)
                .orElseThrow { UserNotFoundException("User mit ID $userId nicht gefunden!") }

            val newMembership = ProjectMemberEntity(
                project = project,
                user = user,
                role = roleFromFrontend
            )
            projectMemberRepository.save(newMembership)
            finalUser = user // Hier nutzen wir die lokal geladene Variable einfach weiter!
        }

        // ☕ Kaffeekonto-Logik bleibt absolut gleich
        val coffeeAccount = coffeeAccountRepository.findById(finalUser.id).orElse(null)

        val userDto = UserResponseDto(
            id = finalUser.id,
            firstName = finalUser.firstName,
            lastName = finalUser.lastName,
            username = finalUser.username,
            coffeeBalance = coffeeAccount?.balance ?: 0f,
            role = coffeeAccount?.role ?: "",
            emoji = coffeeAccount?.emoji ?: "",
            projectIds = finalUser.projectMemberships.map { it.project.id }
        )

        return ProjectMemberDto(user = userDto, projectRole = roleFromFrontend)
    }

    /**
     * 🗑️ Entfernt den User aus der Zwischentabelle eines Projekts
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
     * ☕ Bleibt exakt wie vorher – aktualisiert nur das Kaffeekonto global
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

        return UserResponseDto(
            id = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            username = user.username,
            coffeeBalance = account.balance,
            role = account.role,
            emoji = account.emoji,
            projectIds = user.projectMemberships.map { it.project.id }
        )
    }
}