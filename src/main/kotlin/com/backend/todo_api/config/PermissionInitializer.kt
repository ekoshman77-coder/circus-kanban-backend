package com.backend.todo_api.config

import com.backend.todo_api.data.entity.*
import com.backend.todo_api.data.repository.ActionRepository
import com.backend.todo_api.data.repository.ResourceRepository
import com.backend.todo_api.data.repository.RolePermissionRepository
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.data.repository.ScopeRepository

import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Order(1)
class PermissionInitializer(
    private val scopeRepository: ScopeRepository,
    private val resourceRepository: ResourceRepository,
    private val actionRepository: ActionRepository,
    private val roleRepository: RoleRepository,
    private val rolePermissionRepository: RolePermissionRepository
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        initScopes()
        initResources()
        initActions()
        initRoles()
        initPermissions()
        println("✅ [DatabaseInitializer] RBAC-Initialdaten erfolgreich überprüft/angelegt!")
    }

    private fun initScopes() {
        ScopeType.entries.forEach { scopeType ->
            if (scopeRepository.findByName(scopeType) == null) {
                scopeRepository.save(
                    ScopeEntity(
                        name = scopeType,
                        hierarchyLevel = scopeType.hierarchyLevel
                    )
                )
            }
        }
    }

    private fun initResources() {
        ResourceType.entries.forEach { resourceType ->
            if (resourceRepository.findByName(resourceType) == null) {
                resourceRepository.save(ResourceEntity(name = resourceType))
            }
        }
    }

    private fun initActions() {
        ActionType.entries.forEach { actionType ->
            if (actionRepository.findByName(actionType) == null) {
                actionRepository.save(ActionEntity(name = actionType))
            }
        }
    }

    private fun initRoles() {
        val resourceScope = scopeRepository.findByName(ScopeType.RESOURCE)!!
        val deptScope = scopeRepository.findByName(ScopeType.DEPARTMENT)!!
        val projectScope = scopeRepository.findByName(ScopeType.PROJECT)!!
        val companyScope = scopeRepository.findByName(ScopeType.COMPANY)!!

        createRoleIfNotFound(RoleType.OWNER, resourceScope)
        createRoleIfNotFound(RoleType.MEMBER, deptScope)
        createRoleIfNotFound(RoleType.PROJECT_MANAGER, projectScope)
        createRoleIfNotFound(RoleType.ADMIN, companyScope)
    }

    private fun createRoleIfNotFound(roleType: RoleType, scope: ScopeEntity) {
        if (roleRepository.findByName(roleType) == null) {
            roleRepository.save(RoleEntity(name = roleType, scope = scope))
        }
    }

    private fun initPermissions() {
        if (rolePermissionRepository.count() > 0) return // Wenn schon Rechte da sind, abbrechen

        val noteResource = resourceRepository.findByName(ResourceType.NOTE)!!

        val readAction = actionRepository.findByName(ActionType.READ)!!
        val createAction = actionRepository.findByName(ActionType.CREATE)!!
        val updateAction = actionRepository.findByName(ActionType.UPDATE)!!
        val deleteAction = actionRepository.findByName(ActionType.DELETE)!!

        val resourceScope = scopeRepository.findByName(ScopeType.RESOURCE)!!
        val deptScope = scopeRepository.findByName(ScopeType.DEPARTMENT)!!
        val companyScope = scopeRepository.findByName(ScopeType.COMPANY)!!

        val ownerRole = roleRepository.findByName(RoleType.OWNER)!!
        val memberRole = roleRepository.findByName(RoleType.MEMBER)!!
        val adminRole = roleRepository.findByName(RoleType.ADMIN)!!

        // 1. OWNER: Full Control auf eigene Notizen (RESOURCE-Scope)
        listOf(readAction, createAction, updateAction, deleteAction).forEach { action ->
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = ownerRole,
                    action = action,
                    targetScope = resourceScope,
                    resource = noteResource
                )
            )
        }

        // 2. MEMBER auf DEPARTMENT-Ebene: Darf Notizen der Abteilung LESEN und ERSTELLEN
        listOf(readAction, createAction).forEach { action ->
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = memberRole,
                    action = action,
                    targetScope = deptScope,
                    resource = noteResource
                )
            )
        }

        // 3. MEMBER auf RESOURCE-Ebene: Darf eigene Notizen BEARBEITEN und LÖSCHEN
        listOf(updateAction, deleteAction).forEach { action ->
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = memberRole,
                    action = action,
                    targetScope = resourceScope,
                    resource = noteResource
                )
            )
        }

        // 4. ADMIN: Firmenweit NUR LESEN (COMPANY-Scope)
        rolePermissionRepository.save(
            RolePermissionEntity(
                role = adminRole,
                action = readAction, // 👈 Nur READ für Admin auf COMPANY-Ebene
                targetScope = companyScope,
                resource = noteResource
            )
        )
    }
}