package com.backend.todo_api.dto

data class PlannerSettingsDto(
    var userId: String = "",
    var defaultWorkingHours: Int = 8,
    var primeTimeStartHour: Int = 10,
    var primeTimeEndHour: Int = 18
)