package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.DepartmentSecurityResource
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.toEntity
import com.backend.todo_api.model.toSecurityResource
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class DepartmentService(
    private val departmentRepository: DepartmentRepository,
    private val userRepository: UserRepository,
    private val scopeRepository: ScopeRepository,
    private val userContextResolver: UserContextResolver,
    private val permissionService: PermissionService
) {
    // 1️⃣ Öffentliche Methode (z.B. für Controller / Frontend mit Rechteprüfung)
    fun getDepartmentDtoById(userId: String, departmentId: String): DepartmentDto {
        val department = departmentRepository.findById(departmentId).orElseThrow {
            IllegalArgumentException("Abteilung mit der ID $departmentId nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        val canRead = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.READ,
            resource = department.toSecurityResource()
        )

        if (!canRead) {
            throw ActionForbiddenException("Zugriff verweigert: Du hast keine Berechtigung, diese Abteilung einzusehen.")
        }

        return department.toDto()
    }

    // 2️⃣ Interne Hilfsmethode (für Mapping in UserService / kein Permission-Check)
    fun getDepartmentDtoById(departmentId: String?): DepartmentDto? {
        if (departmentId.isNullOrBlank()) return null
        return departmentRepository.findById(departmentId).map { it.toDto() }.orElse(null)
    }

    // 📋 Gibt die Abteilungen basierend auf den aktiven Kontexten zurück
    fun getAllDepartments(userId: String): List<DepartmentDto> {
        val userContexts = userContextResolver.resolveContexts(userId)
        val resultDepartments = mutableSetOf<DepartmentEntity>()

        for (context in userContexts) {
            val dummyResource = DepartmentSecurityResource(
                departmentId = context.scopeInstanceId
            )

            val hasAccess = permissionService.hasPermission(
                userContexts = listOf(context),
                action = ActionType.READ,
                resource = dummyResource
            )

            if (hasAccess) {
                when (context.scope.name) {
                    // COMPANY (Admin): Sieht ausnahmslos alle Abteilungen
                    ScopeType.COMPANY -> {
                        resultDepartments.addAll(departmentRepository.findAll())
                    }

                    // DEPARTMENT: Sieht die eigene Abteilung
                    ScopeType.DEPARTMENT -> {
                        context.scopeInstanceId?.let { deptId ->
                            departmentRepository.findById(deptId).ifPresent { resultDepartments.add(it) }
                        }
                    }

                    else -> {}
                }
            }
        }

        return resultDepartments.map { it.toDto() }
    }

    // ✨ Erstellt eine Abteilung und gibt das DTO zurück
    @Transactional
    fun createDepartment(userId: String, departmentDto: DepartmentDto): DepartmentDto {
        val userContexts = userContextResolver.resolveContexts(userId)

        val targetResource = DepartmentSecurityResource(departmentId = departmentDto.id.ifBlank { null })

        val canCreate = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.CREATE,
            resource = targetResource
        )

        if (!canCreate) {
            throw ActionForbiddenException("Zugriff verweigert: Du hast keine Berechtigung, diese Abteilung zu erstellen.")
        }

        val trimmedName = departmentDto.name.trim()
        if (departmentRepository.findByNameIgnoreCase(trimmedName) != null) {
            throw IllegalArgumentException("Eine Abteilung mit dem Namen '$trimmedName' existiert bereits.")
        }

        val savedEntity = departmentRepository.save(
            DepartmentEntity(
                name = trimmedName,
                defaultScope = departmentDto.scope.toEntity(scopeRepository)
            )
        )
        return savedEntity.toDto()
    }

    // 📝 Nimmt IDs und Strings, gibt DTO zurück (mit Systemschutz)
    @Transactional
    fun updateDepartment(userId: String, id: String, newName: String): DepartmentDto {
        val department = departmentRepository.findById(id).orElseThrow {
            IllegalArgumentException("Abteilung mit der ID $id nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        val canUpdate = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.UPDATE,
            resource = department.toSecurityResource()
        )

        if (!canUpdate) {
            throw ActionForbiddenException("Zugriff verweigert: Du hast keine Berechtigung, diese Abteilung zu bearbeiten.")
        }

        if (department.name.equals(AppConstants.ADMIN_DEPARTMENT_NAME, ignoreCase = true)) {
            throw IllegalArgumentException("Die System-Abteilung '${AppConstants.ADMIN_DEPARTMENT_NAME}' darf nicht umbenannt werden!")
        }

        val trimmedName = newName.trim()
        val existing = departmentRepository.findByNameIgnoreCase(trimmedName)
        if (existing != null && existing.id != id) {
            throw IllegalArgumentException("Eine andere Abteilung heißt bereits '$trimmedName'.")
        }

        department.name = trimmedName
        val updatedEntity = departmentRepository.save(department)
        return updatedEntity.toDto()
    }

    // 🗑️ Löschen bleibt bei Unit/void, nutzt aber intern den Schutz
    @Transactional
    fun deleteDepartment(userId: String, id: String) {
        val department = departmentRepository.findById(id).orElseThrow {
            IllegalArgumentException("Abteilung mit der ID $id nicht gefunden.")
        }

        val userContexts = userContextResolver.resolveContexts(userId)

        val canDelete = permissionService.hasPermission(
            userContexts = userContexts,
            action = ActionType.DELETE,
            resource = department.toSecurityResource()
        )

        if (!canDelete) {
            throw ActionForbiddenException("Zugriff verweigert: Du hast keine Berechtigung, diese Abteilung zu löschen.")
        }

        if (department.name.equals(AppConstants.ADMIN_DEPARTMENT_NAME, ignoreCase = true)) {
            throw IllegalArgumentException("Die System-Abteilung '${AppConstants.ADMIN_DEPARTMENT_NAME}' kann nicht gelöscht werden!")
        }

        val usersInDepartment = userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, departmentId = department.id)
        if (usersInDepartment.isNotEmpty()) {
            throw IllegalStateException("Die Abteilung kann nicht gelöscht werden, da ihr noch Mitarbeiter zugeordnet sind.")
        }

        departmentRepository.deleteById(id)
    }
}