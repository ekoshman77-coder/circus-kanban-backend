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
import com.backend.todo_api.model.toEntity
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
        createRoleIfNotFound(RoleType.DEPARTMENT_HEAD, deptScope)
        createRoleIfNotFound(RoleType.PROJECT_MANAGER, projectScope)
        createRoleIfNotFound(RoleType.DEVELOPER, projectScope)
        createRoleIfNotFound(RoleType.ADMIN, companyScope)
        createRoleIfNotFound(RoleType.ADMIN_HEAD, companyScope)
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
        val userResource = resourceRepository.findByName(ResourceType.USER)!!
        val departmentResource = resourceRepository.findByName(ResourceType.DEPARTMENT)!!

        val readAction = actionRepository.findByName(ActionType.READ)!!
        val createAction = actionRepository.findByName(ActionType.CREATE)!!
        val updateAction = actionRepository.findByName(ActionType.UPDATE)!!
        val deleteAction = actionRepository.findByName(ActionType.DELETE)!!
        val inviteAction = actionRepository.findByName(ActionType.INVITE)!!

        val resourceScope = scopeRepository.findByName(ScopeType.RESOURCE)!!
        val deptScope = scopeRepository.findByName(ScopeType.DEPARTMENT)!!
        val projectScope = scopeRepository.findByName(ScopeType.PROJECT)!!
        val companyScope = scopeRepository.findByName(ScopeType.COMPANY)!!

        val ownerRole = roleRepository.findByName(RoleType.OWNER)!!
        val memberRole = roleRepository.findByName(RoleType.MEMBER)!!
        val deptHeadRole = roleRepository.findByName(RoleType.DEPARTMENT_HEAD)!!
        val projectManagerRole = roleRepository.findByName(RoleType.PROJECT_MANAGER)!!
        val developerRole = roleRepository.findByName(RoleType.DEVELOPER)!!
        val adminRole = roleRepository.findByName(RoleType.ADMIN)!!
        val adminHeadRole = roleRepository.findByName(RoleType.ADMIN_HEAD)!!

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
                listOf(memberRole, deptHeadRole).forEach { role ->
                    rolePermissionRepository.save(
                        RolePermissionEntity(
                            role = role,
                            action = action,
                            targetScope = deptScope,
                            resource = noteResource
                        )
                    )
                }
            }

            listOf(adminRole, adminHeadRole).forEach { role ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = role,
                        action = readAction,
                        targetScope = companyScope,
                        resource = noteResource
                    )
                )
            }
        }

        // 2. PROJECT PERMISSIONS
        if (!rolePermissionRepository.existsByResource(projectResource)) {
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

            listOf(readAction, createAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = developerRole,
                        action = action,
                        targetScope = deptScope,
                        resource = projectResource
                    )
                )
            }

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

            listOf(readAction, createAction, updateAction, deleteAction).forEach { action ->
                listOf(adminRole, adminHeadRole).forEach { role ->
                    rolePermissionRepository.save(
                        RolePermissionEntity(
                            role = role,
                            action = action,
                            targetScope = companyScope,
                            resource = projectResource
                        )
                    )
                }
            }
        }

        // 3. TODO PERMISSIONS
        if (!rolePermissionRepository.existsByResource(todoResource)) {
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

            listOf(readAction, createAction, updateAction).forEach { action ->
                listOf(memberRole, developerRole).forEach { role ->
                    rolePermissionRepository.save(
                        RolePermissionEntity(
                            role = role,
                            action = action,
                            targetScope = projectScope,
                            resource = todoResource
                        )
                    )
                }
            }

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

        // 4. USER PERMISSIONS
        if (!rolePermissionRepository.existsByResource(userResource)) {
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

            listOf(memberRole, deptHeadRole).forEach { role ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = role,
                        action = readAction,
                        targetScope = deptScope,
                        resource = userResource
                    )
                )
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = role,
                        action = readAction,
                        targetScope = projectScope,
                        resource = userResource
                    )
                )
            }

            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = projectManagerRole,
                    action = readAction,
                    targetScope = projectScope,
                    resource = userResource
                )
            )

            // 👑 ADMIN_HEAD: Volle Kontrolle (READ, CREATE, UPDATE, DELETE, INVITE) firmenweit
            listOf(readAction, createAction, updateAction, deleteAction, inviteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = adminHeadRole,
                        action = action,
                        targetScope = companyScope,
                        resource = userResource
                    )
                )
            }

            // 🛡️ ADMIN: Kontrolle firmenweit (READ, CREATE, UPDATE, INVITE - OHNE DELETE)
            listOf(readAction, createAction, updateAction, inviteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = adminRole,
                        action = action,
                        targetScope = companyScope,
                        resource = userResource
                    )
                )
            }

            // INVITE-Rechte für Führungskräfte/Manager auf User
            listOf(deptHeadRole, projectManagerRole).forEach { role ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = role,
                        action = inviteAction,
                        targetScope = companyScope,
                        resource = userResource
                    )
                )
            }
        }

        // 5. DEPARTMENT PERMISSIONS
        if (!rolePermissionRepository.existsByResource(departmentResource)) {
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = memberRole,
                    action = readAction,
                    targetScope = deptScope,
                    resource = departmentResource
                )
            )

            // 👑 ADMIN_HEAD: Volle Kontrolle firmenweit
            listOf(readAction, createAction, updateAction, deleteAction, inviteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = adminHeadRole,
                        action = action,
                        targetScope = companyScope,
                        resource = departmentResource
                    )
                )
            }

            // 🛡️ ADMIN: Kontrolle firmenweit (OHNE DELETE)
            listOf(readAction, createAction, updateAction, inviteAction).forEach { action ->
                rolePermissionRepository.save(
                    RolePermissionEntity(
                        role = adminRole,
                        action = action,
                        targetScope = companyScope,
                        resource = departmentResource
                    )
                )
            }

            // INVITE-Rechte für Abteilungsleiter auf Abteilungen
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = deptHeadRole,
                    action = inviteAction,
                    targetScope = companyScope,
                    resource = departmentResource
                )
            )
        }
    }
}