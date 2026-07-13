package com.backend.todo_api.data.entity

import com.backend.todo_api.dto.MilestoneDto
import com.backend.todo_api.dto.UserDto
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "milestones")
class MilestoneEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: String = "ms_" + UUID.randomUUID().toString().take(11),

    @Column(nullable = false)
    var title: String = "",

    @Column(nullable = false)
    var duration: Double = 0.0,

    @Column(name = "used_duration", nullable = false)
    var usedDuration: Double = 0.0,

    @Column(nullable = false)
    var status: String = "Offen",

    @Column(name = "order_index")
    var orderIndex: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id", nullable = true)
    var assignedUser: UserEntity? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    var project: ProjectEntity? = null
) {
    constructor() : this(id = "ms_" + UUID.randomUUID().toString().take(11))

    // ✨ Schicke Konvertierungsmethode
    fun toDto(): MilestoneDto {
        val userDto = assignedUser?.let { UserDto(id = it.id, username = it.username, it.firstName, it.lastName) }

        // Wir hängen project?.id als letzten Parameter an den Konstruktor an!
        val dto = MilestoneDto(
            id = id,
            title = title,
            duration =  duration,
            usedDuration = usedDuration,
            status = status,
            assignedUserId =  assignedUser?.id,
            assignedUser = userDto,
            orderIndex = orderIndex,
            projectId =  project?.id // 📁 Hier wird die Projekt-ID flach mitgegeben
        )
        return dto
    }
}