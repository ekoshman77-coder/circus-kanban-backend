package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.model.enums.ScopeType
import org.springframework.data.jpa.repository.JpaRepository

interface ScopeRepository: JpaRepository<ScopeEntity, String> {
    fun findByName(scopeType: ScopeType): ScopeEntity?
}