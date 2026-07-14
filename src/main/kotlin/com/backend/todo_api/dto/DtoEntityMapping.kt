package com.backend.todo_api.dto

import com.backend.todo_api.data.entity.MilestoneEntity
import com.backend.todo_api.data.entity.NoteEntity
import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
import java.util.UUID

// 🛠️ 1. DIE BASIS-EXTENSION (Für die Elternklasse CreateTodoDto)
// Diese Funktion kennt ALLE Felder außer der ID, weil sie auf CreateTodoDto operiert!
fun CreateTodoDto.toEntityWithoutId() = TodoEntity(
    id = UUID.randomUUID().toString(), // Standardmäßig keine ID
    task = this.task,
    description = this.description,
    done = this.done,
    effort = this.effort,
    usedEffort = this.usedEffort,
    dueDate = this.dueDate,
    completedAt = this.completedAt,
    // Wenn das Frontend 0 schickt, generiert das Backend die Zeit, sonst nimmt es die übergebene Zeit
    createdAt = if (this.createdAt == 0L) System.currentTimeMillis() else this.createdAt,
    userId = this.userId,
    category = this.category,
    effortChangesCount = 0,
    milestoneId = this.milestoneId,
    assignedUserId = this.assignedUserId,
    isStarted = this.isStarted,
    teamStatus = this.teamStatus,
    lastDeveloperId = this.lastDeveloperId
)

// 🚀 2. REUSE DURCH VERERBUNG: Extension für POST (Neuerstellung)
// Wir rufen einfach die Basis-Funktion auf. Extrem schlank!
fun CreateTodoDto.toNewEntity(): TodoEntity {
    return this.toEntityWithoutId()
}

// 🚀 3. REUSE DURCH VERERBUNG: Extension für PUT (Update eines bestehenden Kind-DTOs)
// Weil TodoDto von CreateTodoDto erbt, können wir 'toEntityWithoutId()' hier drinnen aufrufen!
// Danach stempeln wir einfach nur noch die ID auf die Entity drauf.
fun TodoDto.toEntity(): TodoEntity {
    val entity = this.toEntityWithoutId() // Nutzt den Code der Elternklasse!
    entity.id = this.id
    entity.effortChangesCount = this.effortChangesCount?: 0// Fügt die ID des Kindes hinzu
    return entity
}

// 🔄 4. Von Entity zu DTO (Für die API-Antworten)
fun TodoEntity.toDto() = TodoDto(
    id = this.id ?: "",
    task = this.task,
    description = this.description,
    done = this.done,
    effort = this.effort,
    usedEffort = this.usedEffort,
    dueDate = this.dueDate,
    completedAt = this.completedAt,
    createdAt = this.createdAt,
    userId = this.userId,
    category = this.category,
    effortChangesCount = this.effortChangesCount,
    milestoneId = this.milestoneId,
    assignedUserId = this.assignedUserId,
    isStarted = this.isStarted,
    teamStatus = this.teamStatus,
    lastDeveloperId = this.lastDeveloperId
)

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
    weatherCode = this.weatherCode
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

