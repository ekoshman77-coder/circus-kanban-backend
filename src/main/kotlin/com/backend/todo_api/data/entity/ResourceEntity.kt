package com.backend.todo_api.data.entity

import com.backend.todo_api.model.ResourceType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "resources")
class ResourceEntity (
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    val name: ResourceType = ResourceType.DEPARTMENT // Enum statt loser String!
)