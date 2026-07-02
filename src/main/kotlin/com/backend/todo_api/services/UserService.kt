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
    private val coffeeAccountRepository: CoffeeAccountRepository
    ) {

    // 🔑 Logik für den Login
    fun login(dto: CreateUserDto): UserDto {
        val usernameTrimmed = dto.username.trim()
        val userEntity = userRepository.findByUsernameIgnoreCase(usernameTrimmed)
            ?: throw UserNotFoundException("Dieser Name existiert nicht. Willst du dich neu registrieren?")

        return UserDto(
            id = userEntity.id,
            username = userEntity.username,
            firstName = userEntity.firstName,
            lastName = userEntity.lastName
        )
    }

    // ✨ Logik für die Registrierung
    @Transactional
    fun register(dto: CreateUserDto): UserDto {
        val usernameTrimmed = dto.username.trim()

        if (userRepository.findByUsernameIgnoreCase(usernameTrimmed) != null) {
            throw UserAlreadyExistsException("Dieser Name ist leider schon vergeben!")
        }

        val savedEntity = userRepository.save(UserEntity(username = usernameTrimmed, firstName = dto.firstName, lastName = dto.lastName))

        val defaultSettings = PlannerSettingsEntity(
            id = savedEntity.id,
            defaultWorkingHours = 8,
            primeTimeStartHour = 10,
            primeTimeEndHour = 18
            )

        // 3. In der Datenbank verewigen
        plannerSettingsRepository.save(defaultSettings)

        val defaultCoffeeAccount = CoffeeAccountEntity(
            userId = savedEntity.id,   // Gleiche ID wie der User!
            balance = 0f,            // Konsequent Float 0.0
            emoji = "👩‍💻",            // Standard-Emoji
            role = "Teammitglied"    // Standard-Rolle
        )
        coffeeAccountRepository.save(defaultCoffeeAccount)

        return UserDto(id = savedEntity.id, username = savedEntity.username, firstName = savedEntity.firstName, lastName = savedEntity.lastName)
    }

    @Transactional
    fun updateUser(id: String, dto: CreateUserDto): UserDto {
        val userEntity = userRepository.findById(id).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $id wurde nicht gefunden.")
        }

        // Der Username ist in der Entity ein 'val', d.h. nicht überschreibbar.
        // Wir aktualisieren die änderbaren Felder:
        userEntity.firstName = dto.firstName
        userEntity.lastName = dto.lastName
        val updatedUser = userRepository.save(userEntity)
        return UserDto(id = updatedUser.id, username = updatedUser.username, firstName = updatedUser.firstName, lastName = updatedUser.lastName)
    }

    @Transactional
    fun deleteUser(id: String) {
        val userEntity = userRepository.findById(id).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $id wurde nicht gefunden.")
        }

        // 1. 📝 TODOs NEUTRALISIEREN
        // Alle To-Dos, die diesem User zugewiesen sind, werden wieder "frei" gegeben
        val assignedTodos = todoRepository.findByAssignedUserId(id)
            assignedTodos.forEach { todo ->
            todo.assignedUserId = null // oder "", je nachdem wie dein Repository/Datenbank mit null umgeht
        }
        todoRepository.saveAll(assignedTodos)

        // 2. 🏁 MEILENSTEINE NEUTRALISIEREN
        // Wir kappen die ManyToOne-Verbindung im Meilenstein
        val assignedMilestones = milestoneRepository.findByAssignedUserId(id)
        assignedMilestones.forEach { milestone ->
            milestone.assignedUser = null
        }
        milestoneRepository.saveAll(assignedMilestones)

        // 3. 📁 PROJEKT-ERSTELLER ABSICHERN
        // Falls der User selbst Projekte erstellt hat (userId == id)
        // Da 'userId' in ProjectEntity 'nullable = false' ist, können wir es nicht auf null setzen.
        // Wir setzen es stattdessen auf einen Platzhalter "SYSTEM" oder einen gelöschten User,
        // damit das Projekt nicht gelöscht werden muss!
        val createdProjects = projectRepository.findByUserId(id)
        createdProjects.forEach { project ->
            project.userId = "DELETED_USER"
        }
        projectRepository.saveAll(createdProjects)

        // 4. 🤝 ZWISCHENTABELLE LEEREN (ManyToMany)
        // Wir nutzen deine schicke Hilfsmethode 'removeTeamMember' aus der ProjectEntity!
        // Da wir über eine Kopie der Liste iterieren müssen (um ConcurrentModificationException zu vermeiden):
        ArrayList(userEntity.projects).forEach { project ->
            project.removeTeamMember(userEntity)
        }
        userEntity.projects.clear()

        // 5. ⚙️ PLANNER SETTINGS LÖSCHEN (Eins-zu-Eins verknüpft)
        plannerSettingsRepository.deleteById(id)

        // 6. ⚰️ USER ENDGÜLTIG LÖSCHEN
        userRepository.delete(userEntity)
    }
}
