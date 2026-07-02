package com.backend.todo_api.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "todo_samples")
class AiFocusSampleEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "sample", nullable = false)
    var sample: String = "",

    @Column(name = "focus_type", nullable = false)
    var focusType: String = "LOW_FOCUS"
)