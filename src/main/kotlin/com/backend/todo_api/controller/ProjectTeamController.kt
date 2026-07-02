package com.backend.todo_api.controller

import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.services.ProjectTeamService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/teams") // Unser 'teamApiUrl' aus dem Frontend!
class ProjectTeamController(
    private val projectTeamService: ProjectTeamService // Unser Service fürs Gehirn
) {

    @GetMapping
    fun getProjectMembers(
        @RequestParam(required = false) projectId: String? // 🎯 NEU: optionaler String und mit "?" erlauben wir null!
    ): ResponseEntity<List<UserResponseDto>> {

        val members = if (projectId.isNullOrBlank()) {
            // 🌍 Fall A: Keine ProjectId übergeben? Dann hol alle User des Systems über das Team-Gehirn!
            projectTeamService.getAllGlobalUsersWithProjects() // Diese Methode rufen wir im Service auf
        } else {
            // 📂 Fall B: ProjectId ist da? Dann filtriere wie gewohnt nach Projekt!
            projectTeamService.getMembersForProject(projectId)
        }

        return ResponseEntity.ok(members)
    }

    /**
     * ➕ ENDPUNKT 2: POST /api/projects
     * Aus Angular kommt: this.http.post(teamApiUrl, body, { params: { projectId } })
     * Der Body enthält: { userId: "..." }
     */
    @PostMapping
    fun assignUserToProject(
        @RequestParam projectId: String,
        @RequestBody request: AssignUserRequestDTO
    ): ResponseEntity<UserResponseDto> {

        // 🎯 HIER BRAUCHEN WIR DEINE LOGIK!
        // Der Service soll den User zum Projekt zuweisen und den zugewiesenen User zurückgeben.

        // Wie würden wir das im Service aufrufen?
        // Val oder var? Und was übergeben wir?

         val assigned = projectTeamService.assignUserToProject(projectId, request.userId)
        return ResponseEntity.ok(assigned)
    }

    /** * 🗑️ DELETE /api/teams/{memberId}?projectId=xyz
     */
    @DeleteMapping("/{memberId}")
    fun removeUserFromProject(
        @RequestParam projectId: String,
        @PathVariable memberId: String
    ): ResponseEntity<Void> {
        // 🎯 HIER rufen wir dein Service-Gehirn auf!
        // Schau kurz nach, ob die Methode in deinem 'ProjectTeamService'
        // exakt "removeUserFromProject" oder vielleicht "deleteMemberFromProject" heißt.
        projectTeamService.removeUserFromProject(projectId, memberId)

        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/coffee-account")
    fun updateCoffeeAccount(
        @PathVariable id: String,
        @RequestParam balance: Float,
        @RequestParam role: String,
        @RequestParam emoji: String
    ): ResponseEntity<UserResponseDto> {
        // Ruft die neue All-in-One-Methode im Service auf
        val updatedUser = projectTeamService.updateCoffeeAccount(id, balance, role, emoji)
        return ResponseEntity.ok(updatedUser)
    }
}

// 📦 Unsere kleinen Daten-Container (DTOs) für den Datenaustausch
data class AssignUserRequestDTO(val userId: String)
