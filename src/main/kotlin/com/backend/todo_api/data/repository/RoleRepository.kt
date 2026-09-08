package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ActionEntity
import com.backend.todo_api.data.entity.DepartmentSpecializationEntity
import com.backend.todo_api.data.entity.ResourceEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.model.RoleType
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository: JpaRepository<RoleEntity, String> {
    fun findByName(owner: RoleType): RoleEntity?

}