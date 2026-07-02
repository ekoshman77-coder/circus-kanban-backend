package com.backend.todo_api.dto

data class IgnoredMilestonesRequest(
    val userId: String,
    val projectTitle: String,
    val milestoneTitles: List<String>
)