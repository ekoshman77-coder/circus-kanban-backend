package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.CoffeeAccountEntity
import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.CoffeeAccountRepository
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.dto.UserApproveDto
import com.backend.todo_api.dto.UserDto
import com.backend.todo_api.dto.copyToUserDto
import com.backend.todo_api.exceptions.UserAlreadyExistsException
import com.backend.todo_api.exceptions.UserNotApprovedException
import com.backend.todo_api.exceptions.UserNotFoundException
import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class UserService(
    private val userRepository: UserRepository,
    private val plannerSettingsRepository: PlannerSettingsRepository,
    private val todoRepository: TodoRepository,
    private val milestoneRepository: MilestoneRepository,
    private val projectRepository: ProjectRepository,
    private val coffeeAccountRepository: CoffeeAccountRepository,
    private val passwordEncoder: org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder,
    private val departmentRepository: DepartmentRepository,
) {

    // 🔑 Login
    fun login(dto: CreateUserDto): UserDto {
        val usernameTrimmed = dto.username.trim()
        val userEntity = userRepository.findByUsernameIgnoreCase(usernameTrimmed)
            ?: throw UserNotFoundException("Dieser Name existiert nicht.")

        if (!passwordEncoder.matches(dto.password, userEntity.password)) {
            throw RuntimeException("Falsches Passwort!")
        }

        if (!userEntity.isApproved) {
            throw UserNotApprovedException("Dein Account befindet sich noch im Warteraum. Ein Admin muss dich erst freischalten.")
        }

        // 🚀 Nutzt deine neue Methode!
        return copyToUserDto(userEntity, UserDto())
    }

    // ✨ Registrierung
    @Transactional
    fun register(dto: CreateUserDto): UserDto {
        val usernameTrimmed = dto.username.trim()

        if (userRepository.findByUsernameIgnoreCase(usernameTrimmed) != null) {
            throw UserAlreadyExistsException("Dieser Name ist leider schon vergeben!")
        }

        val isFirstUser = userRepository.count() == 0L
        var assignedDepartmentId: String? = null
        var approvedStatus = false

        if (isFirstUser) {
            val adminDept = departmentRepository.findByNameIgnoreCase(AppConstants.ADMIN_DEPARTMENT_NAME)
            assignedDepartmentId = adminDept?.id
            approvedStatus = true
            println("👑 Ur-Admin Registrierung erkannt! Gewählte Abteilung: ${AppConstants.ADMIN_DEPARTMENT_NAME}.")
        } else {
            approvedStatus = false
            assignedDepartmentId = null
        }

        val hashedPassword = passwordEncoder.encode(dto.password)
        val savedEntity = userRepository.save(UserEntity(
            username = usernameTrimmed,
            firstName = dto.firstName,
            lastName = dto.lastName,
            password = if (hashedPassword == null)  "" else hashedPassword,
            departmentId = assignedDepartmentId,
            isApproved = approvedStatus
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
            emoji = if (isFirstUser) "👑" else "👩‍💻",
            role = if (isFirstUser) "Admin" else "Teammitglied"
        )
        coffeeAccountRepository.save(defaultCoffeeAccount)

        // 🚀 Nutzt deine neue Methode!
        return copyToUserDto(savedEntity, UserDto())
    }

    @Transactional
    fun updateUser(id: String, dto: UserDto): UserDto {
        val userEntity = userRepository.findById(id).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $id wurde nicht gefunden.")
        }

        if (userEntity.isApproved != dto.isApproved) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Sicherheitswarnung: Statusänderungen (isApproved) sind über diesen Endpunkt nicht erlaubt!"
            )
        }
        userEntity.firstName = dto.firstName
        userEntity.lastName = dto.lastName
        val updatedUser = userRepository.save(userEntity)

        // 🚀 Nutzt deine neue Methode!
        return copyToUserDto(updatedUser, UserDto())
    }

    @Transactional
    fun deleteUser(id: String) {
        val userEntity = userRepository.findById(id).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $id wurde nicht gefunden.")
        }

        val assignedTodos = todoRepository.findByAssignedUserId(id)
        assignedTodos.forEach { todo -> todo.assignedUserId = null }
        todoRepository.saveAll(assignedTodos)

        val assignedMilestones = milestoneRepository.findByAssignedUserId(id)
        assignedMilestones.forEach { milestone -> milestone.assignedUser = null }
        milestoneRepository.saveAll(assignedMilestones)

        val createdProjects = projectRepository.findByUserId(id)
        createdProjects.forEach { project -> project.userId = "DELETED_USER" }
        projectRepository.saveAll(createdProjects)

        ArrayList(userEntity.projectMemberships).forEach { membership ->
            membership.project.removeTeamMember(userEntity)
        }
        userEntity.projectMemberships.clear()

        plannerSettingsRepository.deleteById(id)
        userRepository.delete(userEntity)
    }

    fun getApprovedUsers(): List<UserDto> {
        // 🚀 Nutzt deine neue Methode via map!
        return userRepository.findByIsApproved(true).map { copyToUserDto(it, UserDto()) }
    }

    fun getUnapprovedUsers(): List<UserDto> {
        // 🚀 Nutzt deine neue Methode via map!
        return userRepository.findByIsApproved(false).map { copyToUserDto(it, UserDto()) }
    }

    @Transactional
    fun approveUser(userId: String, dto: UserApproveDto): UserDto {
        val userEntity = userRepository.findById(userId).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $userId wurde nicht gefunden.")
        }

        userEntity.departmentId = dto.departmentId
        userEntity.isApproved = true

        val savedUser = userRepository.save(userEntity)

        // 🚀 Nutzt deine neue Methode!
        return copyToUserDto(savedUser, UserDto())
    }

    fun getUserById(userId: String): UserDto {
        val userEntity = userRepository.findById(userId)
            .orElseThrow{ UserNotFoundException("Benutzer mit der ID $userId wurde nicht gefunden.") }
        return copyToUserDto(userEntity, UserDto())
    }

    // 🛡️ Hilfsmethode für den Security-Stempel
    fun isAdminDepartment(departmentId: String?): Boolean {
        if (departmentId == null) return false

        // Wir suchen nach der Abteilung mit dem Namen "Administration"
        val adminDept = departmentRepository.findByNameIgnoreCase(AppConstants.ADMIN_DEPARTMENT_NAME)

        // Wenn die IDs übereinstimmen, ist der User ein Admin!
        return adminDept?.id == departmentId
    }
}