package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ActionEntity
import com.backend.todo_api.model.ActionType
import org.springframework.data.jpa.repository.JpaRepository

interface ActionRepository: JpaRepository<ActionEntity, String> {
    fun findByName(actionType: ActionType): ActionEntity?
}