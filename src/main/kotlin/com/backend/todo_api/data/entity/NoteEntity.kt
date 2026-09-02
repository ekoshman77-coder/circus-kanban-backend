package com.backend.todo_api.data.entity

import com.backend.todo_api.dto.NoteDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "notes")
class NoteEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    // 👤 Die neue User-Verknüpfung!
    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    @Column(nullable = false)
    var title: String = "",

    @Column(columnDefinition = "TEXT")
    var content: String = "",

    @Column(name = "color_type", nullable = false)
    var colorType: String = "",

    @Column(name = "tag", nullable = true)
    var tag: String? = null,

    @Column(name = "is_in_calculation")
    var isInCalculation: Boolean = false,

    @Column(name = "temperature", nullable = true)
    var temperature: Double? = null,

    @Column(name = "is_archived")
    var isArchived: Boolean = false,

    @Column(name = "weather_code", nullable = true)
    var weatherCode: Int? = null,

    @Column(name = "department_id", nullable = true)
    var departmentId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_id", nullable = false)
    var scope: ScopeEntity = ScopeEntity()
)