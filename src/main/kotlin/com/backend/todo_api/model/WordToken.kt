package com.backend.todo_api.model

data class WordToken(
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
    val isStopWord: Boolean
)