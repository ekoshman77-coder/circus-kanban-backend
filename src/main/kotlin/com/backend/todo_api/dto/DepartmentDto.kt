package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.toEntity

data class DepartmentDto(
    val id: String = "",
    val name: String = "",
    val scope: ScopeType = ScopeType.DEPARTMENT
) {
    public fun toEntity(scopeRepostory: ScopeRepository): DepartmentEntity {
        return DepartmentEntity(
            id = this.id,
            name = this.name,
            scope.toEntity(scopeRepostory)
        )
    }
}

// 🔄 Unser zentraler Mapper für Abteilungen
fun DepartmentEntity.toDto(): DepartmentDto {
    return DepartmentDto(
        id = this.id,
        name = this.name,
        scope = this.defaultScope.name

    )
}

