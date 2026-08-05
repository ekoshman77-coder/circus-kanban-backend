package com.backend.todo_api.data.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "first_name", nullable = false)
    var firstName: String = "",

    @Column(name = "last_name", nullable = false)
    var lastName: String = "",

    @Column(name = "username", unique = true, nullable = false)
    val username: String = "",

    @Column(name = "password", nullable = false) // 👈 NEU: Hier landet der unlesbare BCrypt-Zeichensalat!
    var password: String = "",

    @Column(name = "xp", nullable = false)
    var xp: Int = 0,

    @Column(name = "level", nullable = false)
    var level: Int = 0,

    @Column(name = "level_title", nullable = false)
    var levelTitle: String = "",

    @Column(name = "streak_covered_until")
    var streakCoveredUntil: LocalDateTime? = null,

    @Column(name = "department_id", nullable = true)
    var departmentId: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_role_id")
    var departmentRole: RoleEntity? = null,

    @Column(name = "is_approved", nullable = false)
    var isApproved: Boolean = false,

    @Column(name = "is_archived", nullable = false, columnDefinition = "boolean default false")
    var isArchived: Boolean = false,

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var projectMemberships: MutableList<ProjectMemberEntity> = mutableListOf()
)