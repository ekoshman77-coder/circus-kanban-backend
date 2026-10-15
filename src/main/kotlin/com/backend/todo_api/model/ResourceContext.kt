package com.backend.todo_api.model

data class ResourceContext(
    val resource: ResourceType,
    val instanceId: String?
)