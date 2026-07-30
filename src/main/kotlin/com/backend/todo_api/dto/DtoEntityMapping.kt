package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.MilestoneEntity
import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
import java.util.UUID


fun PlannerSettingsEntity.toDto() = PlannerSettingsDto(
    userId = id?: "",
    defaultWorkingHours = this.defaultWorkingHours,
    primeTimeStartHour = this.primeTimeStartHour,
    primeTimeEndHour = this.primeTimeEndHour
)

fun PlannerSettingsDto.toEntity() = PlannerSettingsEntity(
    id = this.userId?: "",
    defaultWorkingHours = this.defaultWorkingHours,
    primeTimeStartHour = this.primeTimeStartHour,
    primeTimeEndHour = this.primeTimeEndHour
)

fun CreateNoteDto.toEntityWithoutId() = NoteEntity (
    id = UUID.randomUUID().toString(), // Standardmäßig keine ID
    title = this.title,
    content = this.content,
    colorType = this.colorType,
    tag = this.tag,
    userId = this.userId,
    isInCalculation = this.isInCalculation,
    temperature = this.temperature,
    weatherCode = this.weatherCode,
    departmentId = this.departmentId
)

fun CreateNoteDto.toNewEntity() = this.toEntityWithoutId()

fun NoteDto.toEntity(): NoteEntity {
    val entity = toEntityWithoutId()
    entity.id = this.id
    return entity
}

fun NoteEntity.toDto() = NoteDto (
    id = this.id,
    title = this.title,
    content = this.content,
    colorType = this.colorType,
    tag = this.tag,
    userId = this.userId,
    isInCalculation = this.isInCalculation,
    temperature = this.temperature,
    weatherCode = this.weatherCode
)

fun MilestoneDto.toEntity(): MilestoneEntity {
    // 👤 Proxy-User erstellen, falls zugewiesen
    val userEntity = this.assignedUser?.let { uDto ->
        UserEntity(id = uDto.id)
    }

    // 📁 Proxy-Projekt erstellen. Da 'projectId' jetzt im DTO existiert, klappt das perfekt!
    val projectEntity = this.projectId?.let { pId ->
        ProjectEntity(id = pId)
    }

    return MilestoneEntity(
        id = if (this.id.isBlank()) "ms_" + java.util.UUID.randomUUID().toString().take(11) else this.id,
        title = this.title,
        duration = this.duration,
        usedDuration = this.usedDuration,
        status = this.status,
        assignedUser = userEntity,
        orderIndex = this.orderIndex,
        project = projectEntity // Hibernate setzt den Fremdschlüssel in der DB!
    )
}

