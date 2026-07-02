package com.backend.todo_api.data.entity

import jakarta.persistence.*

@Entity
@Table(name = "levels")
class LevelEntity(
    @Id
    @Column(name = "level", nullable = false)
    val level: Int = 0, // z.B. 1, 2, 3

    @Column(name = "required_xp", nullable = false)
    val requiredXp: Int = 0, // z.B. 0, 100, 250

    @Column(name = "title", nullable = false)
    val title: String = "" // z.B. "Anfänger", "Code-Ninja"
)