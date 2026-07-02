package com.backend.todo_api.dto

data class PredictionRequest(
    val text: String,        // Der eingetippte Text
)

data class PredictionResponse(
    val suggestedCategory: String
)

// Für intelligenten Aufwands-Schätzer
data class EffortPredictionResponse(
    val suggestedEffort: Int,       // Die von der KI geschätzten Stunden (z.B. 4)
)

data class FocusPredictionResponse(
    val focus: String
)

