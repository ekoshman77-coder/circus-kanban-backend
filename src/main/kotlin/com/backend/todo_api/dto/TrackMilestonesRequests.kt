package com.backend.todo_api.dto

data class IgnoredMilestonesRequest(
    val userId: String,
    val projectTitle: String,
    val area: String,
    val milestoneTitles: List<String>
)

/**
 * Kleines Datentransfer-Objekt (DTO) für die POST-Requests
 */
data class TrackMilestoneRequest(
    val projectTitle: String,
    val area: String,
    val milestoneTitle: String,
    val userId: String
)