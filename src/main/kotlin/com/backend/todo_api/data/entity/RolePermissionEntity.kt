package com.backend.todo_api.data.entity

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "role_permissions")
class RolePermissionEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    var id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    var role: RoleEntity = RoleEntity(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_id", nullable = false)
    var action: ActionEntity = ActionEntity(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_id", nullable = false)
    var targetScope: ScopeEntity = ScopeEntity(), // Auf welcher Ebene wird es erlaubt?

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_id", nullable = false)
    var resource: ResourceEntity = ResourceEntity() // Auf welcher Ebene wird es erlaubt?

)