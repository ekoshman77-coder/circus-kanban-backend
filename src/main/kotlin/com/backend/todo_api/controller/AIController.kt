package com.backend.todo_api.controller

import com.backend.todo_api.dto.PredictionRequest
import com.backend.todo_api.dto.PredictionResponse
import com.backend.todo_api.dto.EffortPredictionResponse
import com.backend.todo_api.dto.FocusPredictionResponse
import com.backend.todo_api.dto.PlannerFeedbackRequest
import com.backend.todo_api.dto.PlannerRecommendationRequest
import com.backend.todo_api.dto.PlannerRecommendationsResponse
import com.backend.todo_api.dto.RecommendedTodoResponse
import com.backend.todo_api.dto.SnoozyTodoRequest
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.services.BayesPlannerService
import com.backend.todo_api.services.PlannerCoordinator
import com.backend.todo_api.services.TrainManager
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@CrossOrigin(origins = ["http://localhost:4200"])
@RestController
@RequestMapping("/api/ai")
class AIController(private val trainManager: TrainManager,
    private val plannerCoordinator: PlannerCoordinator
) {

    /**
     * 🔮 1. Globaler KI-Kategorievorschlag
     * POST /api/ai/predict
     */
    @PostMapping("/predict")
    fun getPrediction(@RequestBody request: PredictionRequest): PredictionResponse {
        // Da die Vorhersage global läuft, ignorieren wir request.userId einfach im Hintergrund!
        val suggestion = trainManager.trainAndPredictGlobal(
            text = request.text,
            contextType = AiContextType.TODO_CATEGORY
        )
        return PredictionResponse(suggestedCategory = suggestion)
    }

    /**
     * ⏱️ 2. Globaler KI-Aufwandsschätzer
     * POST /api/ai/predict-effort
     */
    @PostMapping("/predict-effort")
    fun predictEffort(@RequestBody request: PredictionRequest): EffortPredictionResponse {
        val estimatedHours = trainManager.predictGlobalEffort(text = request.text)

        return EffortPredictionResponse(
            suggestedEffort = estimatedHours,
        )
    }

    @GetMapping("/todos/categories")
    fun getTodoCategories(): List<String> {
        println("📡 [AIController] GET /todos/categories aufgerufen")
        return trainManager.getGlobalCategories(AiContextType.TODO_CATEGORY)
    }

    /**
     * 📋 2. Symmetrischer Endpunkt für NOTES / IDEEN-Tags
     * GET /api/ai/notes/categories
     */
    @GetMapping("/notes/categories")
    fun getNoteCategories(): List<String> {
        println("📡 [AIController] GET /notes/categories aufgerufen")
        return trainManager.getGlobalCategories(AiContextType.NOTE_TAG)
    }

    @PostMapping("/todo-focus")
    fun predictTodoFocus(@RequestBody request: PredictionRequest): FocusPredictionResponse {
        // 🧠 Wir füttern das trainierte Naive-Bayes Gehirn im RAM mit dem Task-Text!
        val focus = trainManager.predictGlobalFocus(request.text)
        val answer = focus.name //  == FocusType.HIGH_FOCUS? 'HIGH_FOCUS' : "LOW_FOCUS"
        return FocusPredictionResponse(focus = answer) // Gibt HIGH_FOCUS oder LOW_FOCUS zurück
    }

    /**
     * Berechnet die perfekte, smarte Empfehlung für den Planer (inkl. Modus-Codes!)
     * POST /api/ai/planner/recommend
     */
    @PostMapping("/planner/recommend")
    fun getPlannerRecommendation(@RequestBody request: PlannerRecommendationRequest): PlannerRecommendationsResponse {
        // Der smartPlannerService gibt jetzt direkt das neue RecommendedTodoResponse-Objekt
        // mit modeCode und reasonCode zurück!
        return plannerCoordinator.getRecommendations(
            userId = request.userId,
            userEnergy = request.userEnergy,
            workingTimeLeft = request.workingTimeLeft
        )
    }

    /**
     * Nimmt das Nutzer-Feedback entgegen, damit unser System nativ lernt
     * POST /api/ai/planner/feedback
     */
    /**
     * Nimmt das Nutzer-Feedback entgegen und liest den Kontext aus der DB-Runde
     * POST /api/ai/planner/feedback
     */
    @PostMapping("/planner/feedback")
    fun handlePlannerFeedback(@RequestBody request: PlannerFeedbackRequest): ResponseEntity<Unit> {
        plannerCoordinator.processUserFeedback(request)
        return ResponseEntity.ok().build()
    }

    /**
     * Snoozed ein Todo in der DB (ohne KI-Feedback)
     * POST /api/ai/planner/snooze
     */
    @PostMapping("/planner/snooze")
    fun handleSnoozing(@Valid @RequestBody request: SnoozyTodoRequest): ResponseEntity<Unit> {
        plannerCoordinator.snoozeTodoInBackend(request.todoId, request.durationInMin)
        return ResponseEntity.ok().build()
    }
}
