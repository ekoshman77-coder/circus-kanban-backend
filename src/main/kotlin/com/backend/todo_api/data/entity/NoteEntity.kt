package com.backend.todo_api.data.entity

import com.backend.todo_api.dto.NoteDto
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
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
    var weatherCode: Int? = null
) {
    fun updateFromDto(dto: NoteDto) {
        this.title = dto.title
        this.content = dto.content
        this.colorType = dto.colorType
        this.tag = dto.tag
        this.isInCalculation = dto.isInCalculation
        this.temperature = dto.temperature
        this.weatherCode = dto.weatherCode
    }
}