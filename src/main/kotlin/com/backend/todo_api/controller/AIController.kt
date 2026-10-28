package com.backend.todo_api.controller

import com.backend.todo_api.dto.PredictionRequest
import com.backend.todo_api.dto.PredictionResponse
import com.backend.todo_api.dto.EffortPredictionResponse
import com.backend.todo_api.dto.FocusPredictionResponse
import com.backend.todo_api.dto.PlannerFeedbackRequest
import com.backend.todo_api.dto.PlannerRecommendationRequest
import com.backend.todo_api.dto.PlannerRecommendationsResponse
import com.backend.todo_api.dto.PredictReasonRequestDto
import com.backend.todo_api.dto.RecommendedTodoResponse
import com.backend.todo_api.dto.SnoozyTodoRequest
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.SanitizationInput
import com.backend.todo_api.services.BayesPlannerService
import com.backend.todo_api.services.PlannerCoordinator
import com.backend.todo_api.services.TrainManager
import com.backend.todo_api.services.sanitization.SanitizationService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@CrossOrigin(origins = ["http://localhost:4200"])
@RestController
@RequestMapping("/api/ai")
class AIController(private val trainManager: TrainManager,
    private val plannerCoordinator: PlannerCoordinator,
    private val sanitizationService: SanitizationService
) {

    /**
     * 🔮 1. Globaler KI-Kategorievorschlag
     * POST /api/ai/predict
     */
    @PostMapping("/predict")
    fun getPrediction(@RequestBody request: PredictionRequest): PredictionResponse {
        // Da die Vorhersage global läuft, ignorieren wir request.userId einfach im Hintergrund!
        val suggestion = trainManager.trainAndPredictGlobal<String>(
            input = request.text,
            contextType = AiContextType.TODO_CATEGORY,
            { input -> input }
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
        return trainManager.getGlobalCategories<String>(AiContextType.TODO_CATEGORY)
    }

    /**
     * 📋 2. Symmetrischer Endpunkt für NOTES / IDEEN-Tags
     * GET /api/ai/notes/categories
     */
    @GetMapping("/notes/categories")
    fun getNoteCategories(): List<String> {
        println("📡 [AIController] GET /notes/categories aufgerufen")
        return trainManager.getGlobalCategories<String>(AiContextType.NOTE_TAG)
    }

    @PostMapping("/todo-focus")
    fun predictTodoFocus(@RequestBody request: PredictionRequest): FocusPredictionResponse {
        // 🧠 Wir füttern das trainierte Naive-Bayes Gehirn im RAM mit dem Task-Text!
        val focus = trainManager.predictGlobalFocus(request.text)
        val answer = focus.name //  == FocusType.HIGH_FOCUS? 'HIGH_FOCUS' : "LOW_FOCUS"
        return FocusPredictionResponse(focus = answer) // Gibt HIGH_FOCUS oder LOW_FOCUS zurück
    }

    /**
     * 🕵️‍♂️ 3. KI-Vorhersage für Sanitization Reasons
     * POST /api/ai/predict-sanitization
     */
    @PostMapping("/predict-sanitization")
    fun predictSanitizationReason(@RequestBody request: PredictReasonRequestDto): PredictionResponse {
        // 🎯 Wir nutzen die exakte Regel-Extraktion statt des globalen ML-Trainings
        val suggestion = sanitizationService.predictReason(
            selectedText = request.selectedText,
            patternType = request.patternType
        )

        return PredictionResponse(suggestedCategory = suggestion)
    }
    /**
     * 📋 Liste aller bekannten Sanitization-Reasons für das Frontend-Dropdown
     * GET /api/ai/sanitization/categories
     */
    @GetMapping("/sanitization/categories")
    fun getSanitizationCategories(): List<String> {
        println("📡 [AIController] GET /ai/sanitization/categories aufgerufen")
        return trainManager.getGlobalCategories<SanitizationInput>(AiContextType.SANITIZATION_REASON)
    }
}
