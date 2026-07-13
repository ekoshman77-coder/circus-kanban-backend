package com.backend.todo_api.data.entity

import com.backend.todo_api.model.FocusType
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "todos")
class TodoEntity(
    @Id
    // Generiert automatisch eine einzigartige ID, falls keine übergeben wird
    var id: String = UUID.randomUUID().toString(),

    var task: String = "",
    var description: String? = null,
    var done: Boolean = false,
    var dueDate: Long = 0,
    var completedAt: Long? = null,
    var effort: Int = 0,
    var usedEffort: Int = 0,
    var createdAt: Long = 0,

    @Column(name = "user_id", nullable = false)
    var userId: String = "",

    @Column(name = "category", nullable = false)
    var category: String = "Allgemein",

    @Column(name = "effort_changes_count", nullable = false)
    var effortChangesCount: Int = 0,
    var milestoneId: String? = "",
    var assignedUserId: String? = "",
    var isStarted: Boolean = false,

    @Column(name = "focus_type", nullable = false)
    var focusType: String = "LOW_FOCUS",

    @Column(name = "team_status", nullable = false)
    var teamStatus: String = "BACKLOG",

    @Column(name = "is_archived", nullable = false)
    var isArchived: Boolean = false
)