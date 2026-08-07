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
        val noteResource = resourceRepository.findByName(ResourceType.NOTE)!!
        val projectResource = resourceRepository.findByName(ResourceType.PROJECT)!!
        val todoResource = resourceRepository.findByName(ResourceType.TODO)!!

        // 🚀 NEU: User- und Department-Ressourcen laden
        val userResource = resourceRepository.findByName(ResourceType.USER)!!
        val departmentResource = resourceRepository.findByName(ResourceType.DEPARTMENT)!!

        val readAction = actionRepository.findByName(ActionType.READ)!!
        val createAction = actionRepository.findByName(ActionType.CREATE)!!
        val updateAction = actionRepository.findByName(ActionType.UPDATE)!!
        val deleteAction = actionRepository.findByName(ActionType.DELETE)!!

        val resourceScope = scopeRepository.findByName(ScopeType.RESOURCE)!!
        val deptScope = scopeRepository.findByName(ScopeType.DEPARTMENT)!!
        val projectScope = scopeRepository.findByName(ScopeType.PROJECT)!!
        val companyScope = scopeRepository.findByName(ScopeType.COMPANY)!!

        val ownerRole = roleRepository.findByName(RoleType.OWNER)!!
        val memberRole = roleRepository.findByName(RoleType.MEMBER)!!
        val projectManagerRole = roleRepository.findByName(RoleType.PROJECT_MANAGER)!!
        val adminRole = roleRepository.findByName(RoleType.ADMIN)!!

        // 1. NOTE PERMISSIONS
        if (!rolePermissionRepository.existsByResource(noteResource)) {
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

            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = adminRole,
                    action = readAction,
                    targetScope = companyScope,
                    resource = noteResource
                )
            )
        }

        // 2. PROJECT PERMISSIONS
        if (!rolePermissionRepository.existsByResource(projectResource)) {
            // MEMBER: Darf Projekte der eigenen Abteilung LESEN & ERSTELLEN
            listOf(readAction, createAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = memberRole,
                        action = action,
                        targetScope = deptScope,
                        resource = projectResource
                    )
                )
            }

            // PROJECT_MANAGER: Full Control auf PROJECT-Scope (inkl. UPDATE für Team-Zuweisung!)
            listOf(readAction, createAction, updateAction, deleteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = projectManagerRole,
                        action = action,
                        targetScope = projectScope,
                        resource = projectResource
                    )
                )
            }

            // ADMIN: Darf firmenweit Projekte LESEN, ERSTELLEN, EDITIEREN & LÖSCHEN
            listOf(readAction, createAction, updateAction, deleteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = adminRole,
                        action = action,
                        targetScope = companyScope,
                        resource = projectResource
                    )
                )
            }
        }

        // 3. TODO PERMISSIONS
        if (!rolePermissionRepository.existsByResource(todoResource)) {
            // PRIVATE TODOS (Scope: RESOURCE) -> Volle Kontrolle für den Ersteller
            listOf(readAction, createAction, updateAction, deleteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = ownerRole,
                        action = action,
                        targetScope = resourceScope,
                        resource = todoResource
                    )
                )
            }

            // PROJEKT TODOS (Scope: PROJECT) -> Mitglieder dürfen Lesen, Erzeugen, Editieren
            listOf(readAction, createAction, updateAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = memberRole,
                        action = action,
                        targetScope = projectScope,
                        resource = todoResource
                    )
                )
            }

            // PROJEKT MANAGER & ADMIN -> Dürfen Projekt-Todos AUCH löschen
            listOf(readAction, createAction, updateAction, deleteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = projectManagerRole,
                        action = action,
                        targetScope = projectScope,
                        resource = todoResource
                    )
                )
            }
        }

        // 4. 🚀 USER PERMISSIONS (inkl. Cross-Department / PROJECT-Support!)
        if (!rolePermissionRepository.existsByResource(userResource)) {
            // OWNER: Eigenes Profil lesen, bearbeiten und löschen
            listOf(readAction, updateAction, deleteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = ownerRole,
                        action = action,
                        targetScope = resourceScope,
                        resource = userResource
                    )
                )
            }

            // MEMBER: Kollege-Details in der eigenen Abteilung lesen
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = memberRole,
                    action = readAction,
                    targetScope = deptScope,
                    resource = userResource
                )
            )

            // 🎯 NEU: PROJECT SCOPE für USER
            // Jeder, der in einem Projekt ist (z. B. Projektmitglieder oder PM),
            // darf auch User-Details anderer Mitglieder im SELBEN PROJEKT lesen!
            // (Egal aus welcher Abteilung sie stammen!)
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = memberRole,
                    action = readAction,
                    targetScope = projectScope,
                    resource = userResource
                )
            )

            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = projectManagerRole,
                    action = readAction,
                    targetScope = projectScope,
                    resource = userResource
                )
            )

            // ADMIN: Volle Kontrolle firmenweit (Genehmigung, Board, Bearbeiten)
            listOf(readAction, createAction, updateAction, deleteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = adminRole,
                        action = action,
                        targetScope = companyScope,
                        resource = userResource
                    )
                )
            }
        }
    }
}