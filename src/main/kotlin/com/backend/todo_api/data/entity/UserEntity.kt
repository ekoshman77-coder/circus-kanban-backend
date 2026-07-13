package com.backend.todo_api.data.entity

import jakarta.persistence.*
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

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    var projectMemberships: MutableList<ProjectMemberEntity> = mutableListOf()
)