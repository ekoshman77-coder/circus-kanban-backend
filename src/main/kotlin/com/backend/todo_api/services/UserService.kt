package com.backend.todo_api.services

import com.backend.todo_api.data.entity.CoffeeAccountEntity
import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.CoffeeAccountRepository
import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.dto.UserDto
import com.backend.todo_api.dto.toDto
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

// Eigene Exceptions für saubere Fehlerbehandlung im Controller
class UserAlreadyExistsException(message: String) : RuntimeException(message)
class UserNotFoundException(message: String) : RuntimeException(message)

@Service
class UserService(
    private val userRepository: UserRepository,
    private val plannerSettingsRepository: PlannerSettingsRepository,
    private val todoRepository: TodoRepository,
    private val milestoneRepository: MilestoneRepository,
    private val projectRepository: ProjectRepository,
    private val coffeeAccountRepository: CoffeeAccountRepository,
    private val passwordEncoder: org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
) {

    // 🔑 Logik für den Login
    fun login(dto: CreateUserDto): UserDto {
        val usernameTrimmed = dto.username.trim()
        val userEntity = userRepository.findByUsernameIgnoreCase(usernameTrimmed)
            ?: throw UserNotFoundException("Dieser Name existiert nicht.")

        if (!passwordEncoder.matches(dto.password, userEntity.password)) {
            throw RuntimeException("Falsches Passwort!")
        }

        return userEntity.toDto()
    }

    // ✨ Logik für die Registrierung
    @Transactional
    fun register(dto: CreateUserDto): UserDto {
        val usernameTrimmed = dto.username.trim()

        if (userRepository.findByUsernameIgnoreCase(usernameTrimmed) != null) {
            throw UserAlreadyExistsException("Dieser Name ist leider schon vergeben!")
        }

        val hashedPassword = passwordEncoder.encode(dto.password)
        val savedEntity = userRepository.save(UserEntity(
            username = usernameTrimmed,
            firstName = dto.firstName,
            lastName = dto.lastName,
            password = if (hashedPassword == null)  "" else hashedPassword
        ))

        val defaultSettings = PlannerSettingsEntity(
            id = savedEntity.id,
            defaultWorkingHours = 8,
            primeTimeStartHour = 10,
            primeTimeEndHour = 18
        )

        plannerSettingsRepository.save(defaultSettings)

        val defaultCoffeeAccount = CoffeeAccountEntity(
            userId = savedEntity.id,
            balance = 0f,
            emoji = "👩‍💻",
            role = "Teammitglied"
        )
        coffeeAccountRepository.save(defaultCoffeeAccount)

        return savedEntity.toDto()
    }

    @Transactional
    fun updateUser(id: String, dto: CreateUserDto): UserDto {
        val userEntity = userRepository.findById(id).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $id wurde nicht gefunden.")
        }

        userEntity.firstName = dto.firstName
        userEntity.lastName = dto.lastName
        val updatedUser = userRepository.save(userEntity)
        return updatedUser.toDto()
    }

    @Transactional
    fun deleteUser(id: String) {
        val userEntity = userRepository.findById(id).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $id wurde nicht gefunden.")
        }

        // 1. 📝 TODOs NEUTRALISIEREN
        val assignedTodos = todoRepository.findByAssignedUserId(id)
        assignedTodos.forEach { todo ->
            todo.assignedUserId = null
        }
        todoRepository.saveAll(assignedTodos)

        // 2. 🏁 MEILENSTEINE NEUTRALISIEREN
        val assignedMilestones = milestoneRepository.findByAssignedUserId(id)
        assignedMilestones.forEach { milestone ->
            milestone.assignedUser = null
        }
        milestoneRepository.saveAll(assignedMilestones)

        // 3. 📁 PROJEKT-ERSTELLER ABSICHERN
        val createdProjects = projectRepository.findByUserId(id)
        createdProjects.forEach { project ->
            project.userId = "DELETED_USER"
        }
        projectRepository.saveAll(createdProjects)

        // 4. 🤝 ZWISCHENTABELLE LEEREN (Jetzt angepasst an die neue Listen-Struktur!)
        // Wir holen uns das jeweilige Projekt aus der Mitgliedschaft und kappen die Verbindung
        ArrayList(userEntity.projectMemberships).forEach { membership ->
            membership.project.removeTeamMember(userEntity)
        }
        userEntity.projectMemberships.clear()

        // 5. ⚙️ PLANNER SETTINGS LÖSCHEN
        plannerSettingsRepository.deleteById(id)

        // 6. ⚰️ USER ENDGÜLTIG LÖSCHEN
        userRepository.delete(userEntity)
    }
}