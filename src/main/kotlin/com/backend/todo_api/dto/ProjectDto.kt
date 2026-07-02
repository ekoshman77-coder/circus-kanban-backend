package com.backend.todo_api.dto

open class CreateProjectDto {
    var userId: String = ""
    var ideaId: String = ""
    var title: String = ""
    var area: String = ""
    var content: String? = null
    var status: String = "Calculation" // 'Calculation' | 'Active' | 'Zip'
    var milestones: List<CreateMilestoneDto> = emptyList()
    var teamMemberIds: List<String> = emptyList() // IDs für die Verknüpfung beim Speichern

    constructor()
}

class ProjectDto : CreateProjectDto {
    var id: String = ""
    var fullMilestones: List<MilestoneDto> = emptyList()
    var teamMembers: List<UserDto> = emptyList() // Für die voll aufgelösten User-Daten im Frontend

    constructor() : super()
}