package com.backend.todo_api.dto

import com.backend.todo_api.model.ScopeType

data class CreateProjectDto(
    val userId: String = "",
    val ideaId: String = "",
    val title: String = "",
    val area: String = "",
    val content: String? = null,
    val status: String = "Calculation",
    val milestones: List<CreateMilestoneDto> = emptyList(), // Ohne IDs
    val departmentId: String = "",
    val scope: ScopeType = ScopeType.DEPARTMENT
)

// 📥 2. Für Antworten vom Server (GET/PUT-Response)
class ProjectDto {
    var id: String = ""
    var userId: String = ""
    var ideaId: String = ""
    var title: String = ""
    var area: String = ""
    var content: String? = null
    var status: String = "Calculation"
    var milestones: List<MilestoneDto> = emptyList() // ✨ Direkt unter 'milestones'!
    var departmentId: String = ""
    var scope: ScopeType = ScopeType.DEPARTMENT
    var teamMembers: List<ProjectMemberDto> = emptyList()
    constructor()
}