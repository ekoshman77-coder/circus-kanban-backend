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
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.data.repository.TodoRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateUserDto
import com.backend.todo_api.dto.UserApproveDto
import com.backend.todo_api.dto.UserDto
import com.backend.todo_api.dto.copyToUserDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.UserAlreadyExistsException
import com.backend.todo_api.exceptions.UserNotApprovedException
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.UserSecurityResource
import com.backend.todo_api.model.toEntity
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
    private val roleRepository: RoleRepository,
    private val permissionService: PermissionService,
    private val userContextResolver: UserContextResolver
) {

    // 🔑 Login

    fun login(dto: CreateUserDto): UserDto {
        val usernameTrimmed = dto.username.trim()
        val userEntity = userRepository.findByUsernameIgnoreCase(usernameTrimmed)
            ?: throw UserNotFoundException("Dieser Name existiert nicht.")

        // 🛡️ NEU: Wenn der User archiviert ist, darf er sich nicht mehr einloggen
        if (userEntity.isArchived) {
            throw UserNotFoundException("Dieser Account wurde archiviert und ist nicht mehr aktiv.")
        }

        if (!passwordEncoder.matches(dto.password, userEntity.password)) {
            throw RuntimeException("Falsches Passwort!")
        }

        if (!userEntity.isApproved) {
            throw UserNotApprovedException("Dein Account befindet sich noch im Warteraum. Ein Admin muss dich erst freischalten.")
        }

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
        }

        val hashedPassword = passwordEncoder.encode(dto.password)
        val defaultDepartmentRole = RoleType.MEMBER.toEntity(roleRepository)
        val savedEntity = userRepository.save(UserEntity(
            username = usernameTrimmed,
            firstName = dto.firstName,
            lastName = dto.lastName,
            password = if (hashedPassword == null) "" else hashedPassword,
            departmentId = assignedDepartmentId,
            departmentRole = defaultDepartmentRole,
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
            role = "Teammitglied"
        )
        coffeeAccountRepository.save(defaultCoffeeAccount)

        return copyToUserDto(savedEntity, UserDto())
    }

    @Transactional
    fun updateUser(currentUserId: String, targetUserId: String, dto: UserDto): UserDto {

        val userEntity = userRepository.findByIdAndIsArchivedFalse(targetUserId)
            ?: throw UserNotFoundException("Benutzer mit der ID $targetUserId wurde nicht gefunden.")

        val userContexts = userContextResolver.resolveContexts(currentUserId)

        val canAct = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE,
            resource = UserSecurityResource(
                targetUserId = targetUserId,
                departmentId =  userEntity.departmentId,
            )
        )

        if (!canAct) {
            throw ActionForbiddenException("du kannst Daten des Users nicht ändern")
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

        return copyToUserDto(updatedUser, UserDto())
    }

    @Transactional
    fun deleteUser(currentUserId: String, targetUserId: String) {
        val userEntity = userRepository.findByIdAndIsArchivedFalse(targetUserId)
            ?: throw UserNotFoundException("Benutzer mit der ID $targetUserId wurde nicht gefunden.")

        val userContexts = userContextResolver.resolveContexts(currentUserId)

        val canAct = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.DELETE,
            resource = UserSecurityResource(
                targetUserId = targetUserId,
                departmentId =  userEntity.departmentId,
            )
        )

        if (!canAct) {
            throw ActionForbiddenException("du kannst Daten des Users nicht ändern")
        }

        // 1. Zuweisungen bei Team-Todos aufheben
        val assignedTodos = todoRepository.findByAssignedUserId(targetUserId)
        assignedTodos.forEach { todo -> todo.assignedUserId = null }
        todoRepository.saveAll(assignedTodos)

        // 2. Eigene private Todos archivieren (Nutzt das vorhandene isArchived in Todos!)
        val privateTodos = todoRepository.findByUserId(targetUserId)
        privateTodos.forEach { todo -> todo.isArchived = true }
        todoRepository.saveAll(privateTodos)

        // 3. Meilensteine wieder freigeben
        val assignedMilestones = milestoneRepository.findByAssignedUserId(targetUserId)
        assignedMilestones.forEach { milestone -> milestone.assignedUser = null }
        milestoneRepository.saveAll(assignedMilestones)

        // 4. Eigene Projekte auf Dummy-User umschreiben
        val createdProjects = projectRepository.findByUserId(targetUserId)
        createdProjects.forEach { project -> project.userId = "DELETED_USER" }
        projectRepository.saveAll(createdProjects)

        // 5. Aus allen Projekten als aktives Mitglied austreten
        ArrayList(userEntity.projectMemberships).forEach { membership ->
            membership.project.removeTeamMember(userEntity)
        }
        userEntity.projectMemberships.clear()

        // 6. Planner Settings löschen (kann weg, da 1:1 Kopplung)
        plannerSettingsRepository.deleteById(targetUserId)

        // 🎯 7. Der Soft-Delete-Clou: Status auf archiviert setzen
        userEntity.isArchived = true
        userEntity.isApproved = false // Aus dem Warteraum/Board entfernen
        userRepository.save(userEntity)
    }

    fun getApprovedUsers(currentUserId: String): List<UserDto> {
        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val userResource = UserSecurityResource(targetUserId = currentUserId)

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = userResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Abrufen der aktiven Benutzerliste.")
        }

        return userRepository.findByIsApprovedAndIsArchivedFalse(true).map { copyToUserDto(it, UserDto()) }
    }

    // 📋 Für Admin Board: Unberechtigte/Wartende Benutzer abrufen (Warteraum)
    fun getUnapprovedUsers(currentUserId: String): List<UserDto> {
        val userContexts = userContextResolver.resolveContexts(currentUserId)
        val userResource = UserSecurityResource(targetUserId = currentUserId)

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = userResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Abrufen des Warteraums.")
        }

        return userRepository.findByIsApprovedAndIsArchivedFalse(false).map { copyToUserDto(it, UserDto()) }
    }

    // 👑 Admin schaltet Benutzer frei und weist Abteilung zu
    @Transactional
    fun approveUser(currentUserId: String, targetUserId: String, dto: UserApproveDto): UserDto {
        val userContexts = userContextResolver.resolveContexts(currentUserId)

        val targetUser = userRepository.findById(targetUserId).orElseThrow {
            UserNotFoundException("Benutzer mit der ID $targetUserId wurde nicht gefunden.")
        }

        // Da der targetUser noch keine Abteilung hat, übergeben wir null für die departmentId.
        // Ein ADMIN schaltet dank COMPANY-Scope und UPDATE-Action auf USER sauber durch!
        val userResource = UserSecurityResource(targetUserId = targetUserId, departmentId = null)

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE,
            resource = userResource
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Keine Berechtigung zum Freischalten von Benutzern.")
        }

        targetUser.departmentId = dto.departmentId
        targetUser.isApproved = true

        val savedUser = userRepository.save(targetUser)
        return copyToUserDto(savedUser, UserDto())
    }

    fun getUserById(currentUserId: String, targetUserId: String): UserDto {
        val userEntity = userRepository.findByIdAndIsArchivedFalse(targetUserId)
            ?: throw UserNotFoundException("Benutzer mit der ID $targetUserId wurde nicht gefunden.")

        val userContexts = userContextResolver.resolveContexts(currentUserId)

        val hasAccess = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = UserSecurityResource(
                targetUserId = targetUserId,
                departmentId = userEntity.departmentId
            )
        )

        if (!hasAccess) {
            throw ActionForbiddenException("Du hast keine Berechtigung, die Profilinformationen dieses Benutzers einzusehen.")
        }

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