package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.toEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class DepartmentService(
    private val departmentRepository: DepartmentRepository,
    private val userRepository: UserRepository,
    private val scopeRepository: ScopeRepository
    )
{

    // 📋 Gibt jetzt eine Liste von DTOs zurück
    fun getAllDepartments(): List<DepartmentDto> {
        return departmentRepository.findAll().map { it.toDto() }
    }

    // ✨ Erstellt eine Abteilung und gibt das DTO zurück
    @Transactional
    fun createDepartment(departmentDto: DepartmentDto): DepartmentDto {
        val trimmedName = departmentDto.name.trim()
        if (departmentRepository.findByNameIgnoreCase(trimmedName) != null) {
            throw RuntimeException("Eine Abteilung mit dem Namen '$trimmedName' existiert bereits.")
        }
        val savedEntity = departmentRepository.save(
            DepartmentEntity(
                                    name = trimmedName,
                                    defaultScope = departmentDto.scope.toEntity(scopeRepository))
        )
        return savedEntity.toDto()
    }

    // 📝 Nimmt IDs und Strings, gibt DTO zurück (mit Systemschutz)
    @Transactional
    fun updateDepartment(id: String, newName: String): DepartmentDto {
        val department = departmentRepository.findById(id).orElseThrow {
            RuntimeException("Abteilung mit der ID $id nicht gefunden.")
        }

        if (department.name.equals(AppConstants.ADMIN_DEPARTMENT_NAME, ignoreCase = true)) {
            throw RuntimeException("Die System-Abteilung '${AppConstants.ADMIN_DEPARTMENT_NAME}' darf nicht umbenannt werden!")
        }

        val trimmedName = newName.trim()
        val existing = departmentRepository.findByNameIgnoreCase(trimmedName)
        if (existing != null && existing.id != id) {
            throw RuntimeException("Eine andere Abteilung heißt bereits '$trimmedName'.")
        }

        department.name = trimmedName
        val updatedEntity = departmentRepository.save(department)
        return updatedEntity.toDto()
    }

    // 🗑️ Löschen bleibt bei Unit/void, nutzt aber intern den Schutz
    @Transactional
    fun deleteDepartment(id: String) {
        val department = departmentRepository.findById(id).orElseThrow {
            RuntimeException("Abteilung mit der ID $id nicht gefunden.")
        }

        if (department.name.equals(AppConstants.ADMIN_DEPARTMENT_NAME, ignoreCase = true)) {
            throw RuntimeException("Die System-Abteilung '${AppConstants.ADMIN_DEPARTMENT_NAME}' kann nicht gelöscht werden!")
        }

        val usersInDepartment = userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, departmentId = department.id)
        if (usersInDepartment.isNotEmpty()) {
            throw RuntimeException("Die Abteilung kann nicht gelöscht werden, da ihr noch Mitarbeiter zugeordnet sind.")
        }

        departmentRepository.deleteById(id)
    }
}