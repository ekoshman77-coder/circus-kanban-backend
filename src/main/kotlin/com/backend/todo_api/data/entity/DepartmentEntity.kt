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

    // NULLABLE: Bestimmt die Sonderfunktion der Abteilung (ADMIN, AUDIT, HR)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialization_id", nullable = true)
    var specialization: DepartmentSpecializationEntity? = null
)


