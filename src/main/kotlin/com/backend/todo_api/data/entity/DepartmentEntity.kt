package com.backend.todo_api.data.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "departments")
class DepartmentEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "name", unique = true, nullable = false)
    var name: String = "",

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_id", nullable = false)
    var defaultScope: ScopeEntity = ScopeEntity()
)

