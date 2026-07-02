package com.backend.todo_api.model

enum class FocusType {
    HIGH_FOCUS,
    LOW_FOCUS
}

// Dieses Modell nutzen wir, wenn wir Daten innerhalb der Services verarbeiten
data class AiFocusTrainingPair(
    val fullText: String,
    val focusType: FocusType
)