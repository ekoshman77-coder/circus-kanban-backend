package com.backend.todo_api.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "planner_settings")
class PlannerSettingsEntity(
    @Id
    @Column(name = "user_id")
    val id: String = "", // 🔑 Genau dieselbe GUID wie der User!

    @Column(nullable = false)
    var defaultWorkingHours: Int = 8,

    @Column(name = "prime_time_start", nullable = false)
    var primeTimeStartHour: Int = 10,

    @Column(name = "prime_time_end", nullable = false)
    var primeTimeEndHour: Int = 18,
    )