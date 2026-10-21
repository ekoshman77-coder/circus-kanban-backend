package com.backend.todo_api.controller

import com.backend.todo_api.dto.IgnoredMilestonesRequest
import com.backend.todo_api.dto.MilestoneSuggestionsResponse
import com.backend.todo_api.dto.TrackMilestoneRequest
import com.backend.todo_api.services.ProjectSplitterService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@CrossOrigin(origins = ["http://localhost:4200"])
@RestController
@RequestMapping("/api/projects")
class ProjectSplitterController(
    private val projectSplitterService: ProjectSplitterService
) {

    /**
     * Endpunkt 1: Vorschläge live abrufen
     * Beispiel: GET /api/projects/suggest?title=Mein%20neues%20Buch&userId=user-123
     */
    @GetMapping("/suggest")
    fun getMilestoneSuggestions(
        @RequestParam title: String,
        @RequestParam area: String,
        @RequestParam userId: String,
        @RequestParam showAll: Boolean = false,
    ): ResponseEntity<MilestoneSuggestionsResponse> {
        val suggestions = projectSplitterService.suggestMilestones(projectTitle = title, area = area, userId = userId)
        return ResponseEntity.ok(suggestions)
    }

    /**
     * Endpunkt 2: Erfolg tracken (Meilenstein wurde ausgewählt)
     */
    @PostMapping("/track-selection")
    fun trackSelection(
        @RequestBody request: TrackMilestoneRequest
    ): ResponseEntity<Unit> {
        projectSplitterService.trackMilestoneSelection(
            projectTitle = request.projectTitle,
            projectArea = request.area,
            milestoneTitle = request.milestoneTitle,
            userId = request.userId
        )
        return ResponseEntity.ok().build()
    }

    /**
     * Endpunkt 3: Ablehnung tracken (Vorschlag wurde weggeklickt)
     */
    @PostMapping("/track-degradation")
    fun trackDegradation(
        @RequestBody request: TrackMilestoneRequest
    ): ResponseEntity<Unit> {
        projectSplitterService.trackMilestoneDegradation(
            projectTitle = request.projectTitle,
            projectArea = request.area,
            milestoneTitle = request.milestoneTitle,
            userId = request.userId
        )
        return ResponseEntity.ok().build()
    }

    @PostMapping("/track-ignorance")
    fun trackIgnoredMilestones(@RequestBody request: IgnoredMilestonesRequest): ResponseEntity<Void> {

        // Wir nehmen alles direkt aus dem sauberen Request-Objekt
        projectSplitterService.trackMultipleMilestoneDegradations(
            projectTitle = request.projectTitle,
            projectArea = request.area,
            milestoneTitles = request.milestoneTitles,
            userId = request.userId
        )

        return ResponseEntity.ok().build()
    }
}

