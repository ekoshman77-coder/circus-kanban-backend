package com.backend.todo_api.data.entity

import com.backend.todo_api.model.DepartmentSpecializationType
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "department_specializations")
class DepartmentSpecializationEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: String = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    var name: DepartmentSpecializationType = DepartmentSpecializationType.ADMIN,

    val description: String? = null
)