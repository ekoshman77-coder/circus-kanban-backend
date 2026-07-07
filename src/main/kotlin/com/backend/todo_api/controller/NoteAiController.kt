package com.backend.todo_api.controller

import com.backend.todo_api.dto.PredictionRequest
import com.backend.todo_api.dto.PredictionResponse
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.services.TrainManager
import org.springframework.web.bind.annotation.*

@CrossOrigin(origins = ["http://localhost:4200"])
@RestController
@RequestMapping("/api/ai/notes") // 🌟 Eigener Pfad für Notizen-KI!
class NoteAiController(private val trainManager: TrainManager) {

    /**
     * KI-Kategorievorschlag exklusiv für Zettel / Ideen
     * POST /api/ai/notes/predict
     */
    @PostMapping("/predict")
    fun getNotePrediction(@RequestBody request: PredictionRequest): PredictionResponse {
        val suggestion = trainManager.trainAndPredictGlobal(
            text = request.text,
            contextType = AiContextType.NOTE_TAG
        )
        return PredictionResponse(suggestedCategory = suggestion)
    }

//    /**
//     * Alle aktuell genutzten Zettel-Tags/Kategorien abrufen
//     * GET /api/ai/notes/categories
//     */
//    @GetMapping("/categories")
//    fun getNoteCategories(): List<String> {
//        return trainManager.getGlobalCategories(AiContextType.NOTE_TAG)
//    }
}