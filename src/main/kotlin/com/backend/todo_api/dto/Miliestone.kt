package com.backend.todo_api.dto

open class CreateMilestoneDto(
    var title: String = "",
    var duration: Double = 0.0,
    var usedDuration: Double = 0.0,
    var status: String = "Offen", // 'Offen' | 'In Arbeit' | 'Erledigt'
    var assignedUserId: String? = null,
    var projectId: String? = null, // 📁 Die Verbindung zum übergeordneten Projekt!
    var orderIndex: Int = 0 // 🆕 Hier sauber drin
)

// 🚀 Die Kindklasse: Reicht die Werte einfach mit "super(...)" nach oben durch
class MilestoneDto(
    id: String = "",
    title: String = "",
    duration: Double = 0.0,
    usedDuration: Double = 0.0,
    status: String = "Offen",
    assignedUserId: String? = null,
    var assignedUser: UserDto? = null, // 👤 Eigener Zustand für das Kind-DTO
    projectId: String? = null,
    orderIndex: Int = 0
) : CreateMilestoneDto(title, duration, usedDuration, status, assignedUserId, projectId, orderIndex) {

    // Da "id" nicht in CreateMilestoneDto existiert, deklarieren wir sie im Body
    var id: String = id
}