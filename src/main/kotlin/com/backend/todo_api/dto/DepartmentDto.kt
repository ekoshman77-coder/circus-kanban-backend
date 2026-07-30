package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.DepartmentEntity

data class DepartmentDto(
    val id: String = "",
    val name: String = ""
) {
    public fun toEntity(): DepartmentEntity {
        return DepartmentEntity(
            id = this.id,
            name = this.name
        )
    }
}

// 🔄 Unser zentraler Mapper für Abteilungen
fun DepartmentEntity.toDto(): DepartmentDto {
    return DepartmentDto(
        id = this.id,
        name = this.name
    )
}

