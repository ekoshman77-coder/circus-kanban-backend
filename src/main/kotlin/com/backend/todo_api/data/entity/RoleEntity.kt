package com.backend.todo_api.data.entity

import com.backend.todo_api.model.RoleType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "roles")
class RoleEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: String = UUID.randomUUID().toString(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    var name: RoleType = RoleType.MEMBER, // Stark typisiert über Enum!

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_id", nullable = false)
    var scope: ScopeEntity = ScopeEntity()
)