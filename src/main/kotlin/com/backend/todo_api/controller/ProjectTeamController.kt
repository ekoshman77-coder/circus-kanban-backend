package com.backend.todo_api.controller

import com.backend.todo_api.dto.AssignUserRequestDTO
import com.backend.todo_api.dto.ProjectMemberDto
import com.backend.todo_api.dto.UserResponseDto
import com.backend.todo_api.services.ProjectTeamService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/teams") // Unser 'teamApiUrl' aus dem Frontend![cite: 12]
@Tag(name = "Project-Team-Controller", description = "Endpunkte für das Projektteam-Management, Kaffeekassenverwaltung und globale Benutzerpools")
class ProjectTeamController(
    private val projectTeamService: ProjectTeamService
) {

    @GetMapping
    @Operation(
        summary = "Projektmitglieder oder abteilungsspezifischen Benutzerpool abrufen",
        description = "Liefert entweder alle zugewiesenen Mitglieder eines spezifischen Projekts oder (falls keine projectId übergeben wird) alle auswählbaren Kollegen aus der Abteilung des anfragenden Benutzers. Archivierte Benutzer werden automatisch ausgeschlossen."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Liste erfolgreich geladen"),
        ApiResponse(responseCode = "404", description = "Projekt oder anfragender Benutzer nicht gefunden")
    ])
    fun getProjectMembers(
        @RequestParam(required = false) projectId: String?,
        principal: Principal?
         // Zwingend erforderlich für die Abteilungstrennung!
    ): ResponseEntity<List<ProjectMemberDto>> {
        val currentUserId = getUserIdFromPrincipal(principal)
        val members = if (projectId.isNullOrBlank()) {
            // 🌍 Fall A: Abteilungs-Pool (Nur Kollegen aus der eigenen Abteilung)
            projectTeamService.getAllGlobalUsersWithProjects(currentUserId)
        } else {
            // 📂 Fall B: Echte Projektmitglieder inklusive ihrer echten Rolle aus der DB!
            projectTeamService.getMembersForProject(projectId, currentUserId)
        }

        return ResponseEntity.ok(members)
    }

    @PostMapping
    @Operation(
        summary = "Benutzer einem Projekt zuweisen",
        description = "Weist einen aktiven Benutzer einem Projekt mit einer spezifischen Rolle zu. Handelt es sich um einen bereits archivierten Benutzer, wird die Anfrage blockiert."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Benutzer erfolgreich zugewiesen oder Rolle aktualisiert"),
        ApiResponse(responseCode = "400", description = "Ungültige Eingabedaten oder Rolle fehlt"),
        ApiResponse(responseCode = "404", description = "Projekt oder Benutzer nicht gefunden"),
        ApiResponse(responseCode = "409", description = "Operation abgelehnt: Benutzer existiert nicht")
    ])
    fun assignUserToProject(
        @RequestParam projectId: String,
        @RequestParam role: String,
        @jakarta.validation.Valid @RequestBody request: AssignUserRequestDTO,
        principal: Principal?
    ): ResponseEntity<ProjectMemberDto> {
        val currentUserId = getUserIdFromPrincipal(principal)
        val assigned = projectTeamService.assignUserToProject(currentUserId, projectId, request.userId, role)
        return ResponseEntity.ok(assigned)
    }

    /** * 🗑️ DELETE /api/teams/{memberId}?projectId=xyz
     */
    @DeleteMapping("/{memberId}")
    @Operation(
        summary = "Benutzer aus einem Projekt entfernen",
        description = "Löscht die Projektmitgliedschaft (die Zuordnung) eines bestimmten Benutzers aus dem angegebenen Projekt."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Benutzer erfolgreich aus dem Projekt entfernt"),
        ApiResponse(responseCode = "404", description = "Projekt, Benutzer oder Mitgliedschaft nicht gefunden")
    ])
    fun removeUserFromProject(
        @RequestParam projectId: String,
        @PathVariable memberId: String,
        principal: Principal?
    ): ResponseEntity<Void> {
        val currentUserId = getUserIdFromPrincipal(principal)
        projectTeamService.removeUserFromProject(projectId, memberId, currentUserId)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/coffee-account")
    @Operation(
        summary = "Kaffeekonto eines Benutzers aktualisieren",
        description = "Aktualisiert Guthaben, Rolle und Emoji der Kaffeekasse für einen spezifischen Benutzer. Für archivierte Benutzer ist das Konto gesperrt."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Kaffeekonto erfolgreich aktualisiert"),
        ApiResponse(responseCode = "404", description = "Benutzerkonto nicht gefunden"),
        ApiResponse(responseCode = "409", description = "Benutzer existiert nicht")
    ])
    fun updateCoffeeAccount(
        @PathVariable id: String,
        @RequestParam balance: Float,
        @RequestParam role: String,
        @RequestParam emoji: String,
        principal: Principal?
    ): ResponseEntity<UserResponseDto> {
        val currentUserId = getUserIdFromPrincipal(principal)
        val updatedUser = projectTeamService.updateCoffeeAccount( currentUserId, id, balance, role, emoji)
        return ResponseEntity.ok(updatedUser)
    }

    @GetMapping("/all-users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Alle aktiven Benutzer für das Admin-Board abrufen",
        description = "Liefert eine vollständige Liste aller registrierten Benutzer (sowohl freigeschaltete als auch im Warteraum befindliche), schließt archivierte (soft-gelöschte) Benutzer jedoch konsequent aus."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Admin-Benutzerliste erfolgreich geladen")
    ])
    fun getAllUsersForAdmin( principal: Principal? ): ResponseEntity<List<ProjectMemberDto>> {
        val currentUserId = getUserIdFromPrincipal(principal)
        val allUsers = projectTeamService.getAllUsersForAdminBoard(currentUserId)
        return ResponseEntity.ok(allUsers)
    }
}