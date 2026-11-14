package com.backend.todo_api.model

import com.backend.todo_api.model.enums.ResourceType

data class ResourceContext(
    val resource: ResourceType,
    val instanceId: String?
)