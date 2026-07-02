package com.backend.todo_api.dto

data class MilestoneSuggestionsResponse(
    val recommended: List<MilestoneSuggestionDto>, // Alles mit Score >= 0
    val degraded: List<MilestoneSuggestionDto>     // Alles mit Score < 0 (die Strafbank)
)