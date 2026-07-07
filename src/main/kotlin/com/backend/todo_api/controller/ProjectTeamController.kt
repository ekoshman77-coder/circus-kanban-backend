package com.backend.todo_api.controller

import com.backend.todo_api.dto.AssignUserRequestDTO
import com.backend.todo_api.dto.ProjectMemberDto
import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.services.ProjectTeamService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/teams") // Unser 'teamApiUrl' aus dem Frontend!
class ProjectTeamController(
    private val projectTeamService: ProjectTeamService
) {

    @GetMapping
    fun getProjectMembers(
        @RequestParam(required = false) projectId: String?
    ): ResponseEntity<List<ProjectMemberDto>> { // 👈 Gibt jetzt immer ProjectMemberDto zurück!

        val members = if (projectId.isNullOrBlank()) {
            // 🌍 Fall A: Globaler Pool (Alle User im System bekommen 'NONE' als Projekt-Rolle)
            projectTeamService.getAllGlobalUsersWithProjects()
        } else {
            // 📂 Fall B: Echte Projektmitglieder inklusive ihrer echten Rolle aus der DB!
            projectTeamService.getMembersForProject(projectId)
        }

        return ResponseEntity.ok(members)
    }

    /**
     * ➕ Weist einen bestehenden User einem bestimmten Projekt mit einer Rolle zu
     * POST /api/teams?projectId=xyz&role=DEVELOPER
     * Body enthält: { "userId": "..." }
     */
    @PostMapping
    fun assignUserToProject(
        @RequestParam projectId: String,
        @RequestParam role: String,
        @jakarta.validation.Valid @RequestBody request: AssignUserRequestDTO // 🎯 HIER muss @Valid stehen!
    ): ResponseEntity<ProjectMemberDto> {
        val assigned = projectTeamService.assignUserToProject(projectId, request.userId, role)
        return ResponseEntity.ok(assigned)
    }

    /** * 🗑️ DELETE /api/teams/{memberId}?projectId=xyz
     */
    @DeleteMapping("/{memberId}")
    fun removeUserFromProject(
        @RequestParam projectId: String,
        @PathVariable memberId: String
    ): ResponseEntity<Void> {
        projectTeamService.removeUserFromProject(projectId, memberId)
        return ResponseEntity.noContent().build()
    }

    /**
     * ☕ Bleibt wie es ist, da das Kaffeekonto rein an den User (global) gebunden ist!
     */
    @PutMapping("/{id}/coffee-account")
    fun updateCoffeeAccount(
        @PathVariable id: String,
        @RequestParam balance: Float,
        @RequestParam role: String,
        @RequestParam emoji: String
    ): ResponseEntity<UserResponseDto> {
        val updatedUser = projectTeamService.updateCoffeeAccount(id, balance, role, emoji)
        return ResponseEntity.ok(updatedUser)
    }
}