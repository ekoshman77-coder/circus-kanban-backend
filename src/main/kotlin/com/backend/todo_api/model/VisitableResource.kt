package com.backend.todo_api.model

import com.backend.todo_api.model.enums.ResourceType

interface VisitableResource {
    val resourceType: ResourceType

    // Die Ressource prüft selbst gegen den einzelnen UserContext
    fun matchesScope(context: UserContext): Boolean
}