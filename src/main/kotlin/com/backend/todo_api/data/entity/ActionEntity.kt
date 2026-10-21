package com.backend.todo_api.data.entity

import com.backend.todo_api.model.ActionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "actions")
class ActionEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    val name: ActionType = ActionType.CREATE // Enum statt loser String!
)