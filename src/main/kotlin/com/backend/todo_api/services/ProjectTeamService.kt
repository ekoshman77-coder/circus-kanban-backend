package com.backend.todo_api.services

import com.backend.todo_api.data.entity.CoffeeAccountEntity
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.data.repository.CoffeeAccountRepository // ☕ DEIN NEUES REPOSITORY!
import com.backend.todo_api.dto.UserResponseDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectTeamService(
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val coffeeAccountRepository: CoffeeAccountRepository // 👈 Hier injizieren!
) {

    /**
     * 📥 Holt alle Teammitglieder eines Projekts und verheiratet sie mit den Kaffeedaten
     */
    @Transactional(readOnly = true)
    fun getMembersForProject(projectId: String): List<UserResponseDto> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Projekt mit ID $projectId nicht gefunden!") }

        return project.teamMembers.map { user ->
            // ☕ Kaffeestand aus der separaten Tabelle laden (Zustand suchen via userId)
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            val currentBalance = coffeeAccount?.balance ?: 0f

            UserResponseDto(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
                coffeeBalance = coffeeAccount.balance,
                emoji = coffeeAccount.emoji,
                role = coffeeAccount.role,
                projectIds = user.projects.map { it.id }
            )
        }
    }

    /**
     * ➕ Fügt ein einzelnes Mitglied einem bestimmten Projekt hinzu
     */
    @Transactional
    fun assignUserToProject(projectId: String, userId: String): UserResponseDto {
        // 1. Projekt laden
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Projekt mit ID $projectId nicht gefunden!") }

        // 2. User laden
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Benutzer mit ID $userId nicht gefunden!") }

        // 3. Dem Projekt hinzufügen
        project.addTeamMember(user)

        // 4. Projekt abspeichern
        val updatedUser = userRepository.save(user) // Wir speichern den User/die Beziehung
        projectRepository.save(project)
        val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)

        // 5. Nur diesen EINEN User als DTO zurückgeben
        return UserResponseDto(
            id = updatedUser.id,
            firstName = updatedUser.firstName,
            lastName = updatedUser.lastName,
            username = updatedUser.username,
            coffeeBalance = coffeeAccount.balance,
            role = coffeeAccount.role,
            emoji = coffeeAccount.emoji,
            projectIds = updatedUser.projects.map { it.id }
        )
    }

    /**
     * 🌍 GLOBALE METHODE: Holt alle registrierten Benutzer der gesamten App,
     * inklusive ihrer Projekt-IDs und dem reinen Float-Kaffeeguthaben.
     */
    @Transactional(readOnly = true)
    fun getAllGlobalUsersWithProjects(): List<UserResponseDto> {
        val allUsers = userRepository.findAll()

        return allUsers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            UserResponseDto(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
                coffeeBalance = coffeeAccount.balance,
                role = coffeeAccount.role,
                emoji = coffeeAccount.emoji,
                projectIds = user.projects.map { it.id } // Sammelt alle Projekt-IDs des Users
            )
        }
    }

    /**
     * 📥 Holt den globalen Pool ALLER registrierten User für die Kaffeekasse
     */
    @Transactional(readOnly = true)
    fun getAllGlobalMembers(): List<UserResponseDto> {
        val allUsers = userRepository.findAll()
        return allUsers.map { user ->
            // ☕ Auch hier für jeden User den Kaffeestand zusammensuchen
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            val currentBalance = coffeeAccount?.balance ?: 0f

            UserResponseDto(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
                coffeeBalance = currentBalance,
                role = coffeeAccount.role,
                emoji = coffeeAccount.emoji,
                projectIds = user.projects.map { it.id }
            )
        }
    }

    @Transactional
    fun removeUserFromProject(projectId: String, userId: String): List<UserResponseDto> {
        // 1. Projekt laden
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Projekt mit ID $projectId nicht gefunden!") }

        // 2. User laden
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Benutzer mit ID $userId nicht gefunden!") }

        // 3. Die Verbindung kappen (Nutzt deine Hilfsmethode aus ProjectEntity)
        project.removeTeamMember(user)

        // 4. Projekt abspeichern
        projectRepository.save(project)

        // 5. Die restlichen verbliebenen Teammitglieder als DTO-Liste zurückgeben
        return project.teamMembers.map { member ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            UserResponseDto(
                id = member.id,
                firstName = member.firstName,
                lastName = member.lastName,
                username = member.username,
                coffeeBalance = coffeeAccount.balance,
                role = coffeeAccount.role,
                emoji = coffeeAccount.emoji,
                projectIds = member.projects.map { it.id }
            )
        }
    }

    /**
     * 🔄 Teamboard-Zuweisung: Aktualisiert die Mitgliederliste eines Projekts
     */
    @Transactional
    fun updateProjectTeam(projectId: String, memberIds: List<String>): List<UserResponseDto> {
        val project = projectRepository.findById(projectId)
            .orElseThrow { IllegalArgumentException("Projekt mit ID $projectId nicht gefunden!") }

        ArrayList(project.teamMembers).forEach { user ->
            project.removeTeamMember(user)
        }

        val freshUsers = userRepository.findAllById(memberIds)
        freshUsers.forEach { user ->
            project.addTeamMember(user)
        }

        projectRepository.save(project)

        return project.teamMembers.map { user ->
            val coffeeAccount = coffeeAccountRepository.findById(user.id).orElse(null)
            val currentBalance = coffeeAccount?.balance ?: 0f

            UserResponseDto(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                username = user.username,
                coffeeBalance = coffeeAccount.balance,
                role = coffeeAccount.role,
                emoji = coffeeAccount.emoji,
                projectIds = user.projects.map { it.id }
            )
        }
    }

    /**
     * ☕ UPDATER: Aktualisiert stumpf das Kaffeekassen-Guthaben in der separaten Tabelle.
     */
    @Transactional
    fun updateCoffeeAccount(userId: String, balance: Float, role: String, emoji: String): UserResponseDto {
        // 1. Kaffeekonto laden
        val account = coffeeAccountRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("Konto nicht gefunden") }

        // 2. Alle 3 Werte auf einmal setzen
        account.balance = balance
        account.role = role
        account.emoji = emoji

        // 3. In der DB speichern
        coffeeAccountRepository.save(account)

        // 4. User für das DTO laden
        val user = userRepository.findById(userId).get()

        return UserResponseDto(
            id = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            username = user.username,
            coffeeBalance = account.balance,
            role = account.role,   // 🟢 Jetzt live aus der DB
            emoji = account.emoji, // 🟢 Jetzt live aus der DB
            projectIds = user.projects.map { it.id }
        )
    }
}