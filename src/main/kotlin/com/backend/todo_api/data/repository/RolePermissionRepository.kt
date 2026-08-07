package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.ResourceEntity
import com.backend.todo_api.data.entity.RolePermissionEntity
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.RoleType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RolePermissionRepository : JpaRepository<RolePermissionEntity, String> {

    fun findByRoleNameInAndActionNameAndResourceName(
        roleNames: List<RoleType>,
        actionName: ActionType,
        resourceName: ResourceType
    ): List<RolePermissionEntity>

    fun findByActionNameAndResourceName(actionName: ActionType, resourceName: ResourceType): List<RolePermissionEntity>
    fun existsByResource(resource: ResourceEntity): Boolean
}