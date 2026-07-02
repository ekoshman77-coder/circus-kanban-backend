package com.backend.todo_api.data.entity

import jakarta.persistence.*

@Entity
@Table(name = "db_versions")
class DbVersionEntity(
    @Id
    val id: String = "LEVEL_SCHEMA", // Ein fester Schlüssel für diesen Zweck

    @Column(name = "version", nullable = false)
    var version: Int = 1
)