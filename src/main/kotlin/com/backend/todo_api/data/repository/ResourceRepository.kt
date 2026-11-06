package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ResourceEntity
import com.backend.todo_api.model.enums.ResourceType
import org.springframework.data.jpa.repository.JpaRepository

interface ResourceRepository: JpaRepository<ResourceEntity, String> {
    fun findByName(resourceType: ResourceType): ResourceEntity?
}