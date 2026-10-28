package com.backend.todo_api.controller

import com.backend.todo_api.dto.PlannerFeedbackRequest
import com.backend.todo_api.dto.PlannerRecommendationRequest
import com.backend.todo_api.dto.PlannerRecommendationsResponse
import com.backend.todo_api.dto.SnoozyTodoRequest
import com.backend.todo_api.services.PlannerCoordinator
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@CrossOrigin(origins = ["http://localhost:4200"])
@RestController
@RequestMapping("/api/ai/planner")
class PlannerController(
    private val plannerCoordinator: PlannerCoordinator
) {

    /**
     * Berechnet die perfekte, smarte Empfehlung für den Planer (inkl. Modus-Codes!)
     * POST /api/ai/planner/recommend
     */
    @PostMapping("/recommend")
    fun getPlannerRecommendation(@RequestBody request: PlannerRecommendationRequest): PlannerRecommendationsResponse {
        return plannerCoordinator.getRecommendations(
            userId = request.userId,
            userEnergy = request.userEnergy,
            workingTimeLeft = request.workingTimeLeft
        )
    }

    /**
     * Nimmt das Nutzer-Feedback entgegen und liest den Kontext aus der DB-Runde
     * POST /api/ai/planner/feedback
     */
    @PostMapping("/feedback")
    fun handlePlannerFeedback(@RequestBody request: PlannerFeedbackRequest): ResponseEntity<Unit> {
        plannerCoordinator.processUserFeedback(request)
        return ResponseEntity.ok().build()
    }

    /**
     * Snoozed ein Todo in der DB (ohne KI-Feedback)
     * POST /api/ai/planner/snooze
     */
    @PostMapping("/snooze")
    fun handleSnoozing(@Valid @RequestBody request: SnoozyTodoRequest): ResponseEntity<Unit> {
        plannerCoordinator.snoozeTodoInBackend(request.todoId, request.durationInMin)
        return ResponseEntity.ok().build()
    }

    /**
     * ☕ Holt die morgendlichen Pause- und Routine-Empfehlungen (effort == 0)
     * GET /api/ai/planner/morning?userId=...
     */
    @GetMapping("/morning")
    fun getMorningRecommendations(
        principal: Principal
    ): PlannerRecommendationsResponse {
        val userId = getUserIdFromPrincipal(principal)
        return plannerCoordinator.getMorningRecommendations(userId)
    }
}