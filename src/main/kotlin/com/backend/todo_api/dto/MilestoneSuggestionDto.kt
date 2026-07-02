package com.backend.todo_api.dto

data class MilestoneSuggestionDto(
    val title: String,
    val score: Int,
    val words: List<String>
)