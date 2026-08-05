package com.backend.todo_api.data.entity

import com.backend.todo_api.model.ScopeType
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "scopes")
class ScopeEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    val name: ScopeType = ScopeType.RESOURCE, // Enum statt loser String!

    @Column(name = "hierarchy_level", nullable = false)
    val hierarchyLevel: Int = 0
)