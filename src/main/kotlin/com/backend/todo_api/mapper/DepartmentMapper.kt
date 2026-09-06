package com.backend.todo_api.mapper

import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.repository.DepartmentSpecializationRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.dto.CreateDepartmentDto
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.model.toEntity
import org.springframework.stereotype.Component

@Component
class DepartmentMapper(
    private val scopeRepository: ScopeRepository,
    private val specializationRepository: DepartmentSpecializationRepository
) {
    // 1. Lesen: Entity -> DTO
    fun toDto(entity: DepartmentEntity): DepartmentDto {
        return DepartmentDto(
            id = entity.id,
            name = entity.name,
            scope = entity.defaultScope.name,
            specialization = entity.specialization?.name
        )
    }

    // 2. Erstellen: CreateDto -> Neue Entity
    fun toEntity(dto: CreateDepartmentDto): DepartmentEntity {
        val scopeEntity = dto.scope.toEntity(scopeRepository)
        val specEntity = dto.specialization?.toEntity(specializationRepository)

        return DepartmentEntity(
            name = dto.name.trim(),
            defaultScope = scopeEntity,
            specialization = specEntity
        )
    }

    // 3. Aktualisieren: Kopiert DTO-Daten direkt in die bestehende Entity
    fun updateEntityFromDto(dto: DepartmentDto, entity: DepartmentEntity): DepartmentEntity {
        val scopeEntity = dto.scope.toEntity(scopeRepository)
        val specEntity = dto.specialization?.toEntity(specializationRepository)

        entity.name = dto.name
        entity.defaultScope = scopeEntity
        entity.specialization = specEntity

        return entity
    }
}