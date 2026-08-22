package com.backend.todo_api.config

import com.backend.todo_api.data.entity.*
import com.backend.todo_api.data.repository.*
import com.backend.todo_api.model.*
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

    // --- PERMISSION ORCHESTRATION ---

    private fun initPermissions() {
        initNotePermissions()
        initProjectPermissions()
        initTodoPermissions()
        initUserPermissions()
        initDepartmentPermissions()
        initPermissionManagementPermissions()
    }

    // --- HELPER METHOD FOR CLEAN & DRY SAVING ---

    private fun savePermissionIfNotExists(
        roleType: RoleType,
        resourceType: ResourceType,
        actionType: ActionType,
        scopeType: ScopeType
    ) {
        val role = roleRepository.findByName(roleType)!!
        val resource = resourceRepository.findByName(resourceType)!!
        val action = actionRepository.findByName(actionType)!!
        val scope = scopeRepository.findByName(scopeType)!!

        val exists = rolePermissionRepository.existsByRoleNameAndResourceNameAndActionName(
            roleType, resourceType, actionType
        )

        if (!exists) {
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = role,
                    resource = resource,
                    action = action,
                    targetScope = scope
                )
            )
        }
    }

    // --- MODULE PERMISSIONS ---

    private fun initNotePermissions() {
        val noteResource = resourceRepository.findByName(ResourceType.NOTE)!!
        if (rolePermissionRepository.existsByResource(noteResource)) return

        // OWNER: Full Access auf Resource
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.OWNER, ResourceType.NOTE, action, ScopeType.RESOURCE)
        }

        // MEMBER & DEPT_HEAD: READ, CREATE in Department
        listOf(ActionType.READ, ActionType.CREATE).forEach { action ->
            savePermissionIfNotExists(RoleType.MEMBER, ResourceType.NOTE, action, ScopeType.DEPARTMENT)
            savePermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.NOTE, action, ScopeType.DEPARTMENT)
        }

        // ADMIN & ADMIN_HEAD: READ in Company
        savePermissionIfNotExists(RoleType.ADMIN, ResourceType.NOTE, ActionType.READ, ScopeType.COMPANY)
        savePermissionIfNotExists(RoleType.ADMIN_HEAD, ResourceType.NOTE, ActionType.READ, ScopeType.COMPANY)
    }

    private fun initProjectPermissions() {
        val projectResource = resourceRepository.findByName(ResourceType.PROJECT)!!
        if (rolePermissionRepository.existsByResource(projectResource)) return

        // MEMBER & DEVELOPER: READ, CREATE in Department
        listOf(ActionType.READ, ActionType.CREATE).forEach { action ->
            savePermissionIfNotExists(RoleType.MEMBER, ResourceType.PROJECT, action, ScopeType.DEPARTMENT)
            savePermissionIfNotExists(RoleType.DEVELOPER, ResourceType.PROJECT, action, ScopeType.DEPARTMENT)
        }

        // PROJECT_MANAGER: Full Access in Project
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.PROJECT, action, ScopeType.PROJECT)
        }

        // ADMIN & ADMIN_HEAD: Full Access in Company
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.ADMIN, ResourceType.PROJECT, action, ScopeType.COMPANY)
            savePermissionIfNotExists(RoleType.ADMIN_HEAD, ResourceType.PROJECT, action, ScopeType.COMPANY)
        }
    }

    private fun initTodoPermissions() {
        val todoResource = resourceRepository.findByName(ResourceType.TODO)!!
        if (rolePermissionRepository.existsByResource(todoResource)) return

        // OWNER: Full Access auf Resource
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.OWNER, ResourceType.TODO, action, ScopeType.RESOURCE)
        }

        // MEMBER & DEVELOPER: READ, CREATE, UPDATE in Project
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE).forEach { action ->
            savePermissionIfNotExists(RoleType.MEMBER, ResourceType.TODO, action, ScopeType.PROJECT)
            savePermissionIfNotExists(RoleType.DEVELOPER, ResourceType.TODO, action, ScopeType.PROJECT)
        }

        // PROJECT_MANAGER: Full Access in Project
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.TODO, action, ScopeType.PROJECT)
        }
    }

    private fun initUserPermissions() {
        val userResource = resourceRepository.findByName(ResourceType.USER)!!
        if (rolePermissionRepository.existsByResource(userResource)) return

        // OWNER: READ, UPDATE, DELETE auf Resource
        listOf(ActionType.READ, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.OWNER, ResourceType.USER, action, ScopeType.RESOURCE)
        }

        // MEMBER & DEPT_HEAD: READ in Department & Project
        listOf(RoleType.MEMBER, RoleType.DEPARTMENT_HEAD).forEach { role ->
            savePermissionIfNotExists(role, ResourceType.USER, ActionType.READ, ScopeType.DEPARTMENT)
            savePermissionIfNotExists(role, ResourceType.USER, ActionType.READ, ScopeType.PROJECT)
        }

        // PROJECT_MANAGER: READ in Project
        savePermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.USER, ActionType.READ, ScopeType.PROJECT)

        // ADMIN_HEAD: Full Access + INVITE in Company
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE, ActionType.INVITE).forEach { action ->
            savePermissionIfNotExists(RoleType.ADMIN_HEAD, ResourceType.USER, action, ScopeType.COMPANY)
        }

        // ADMIN: READ, CREATE, UPDATE, INVITE in Company (ohne DELETE)
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.INVITE).forEach { action ->
            savePermissionIfNotExists(RoleType.ADMIN, ResourceType.USER, action, ScopeType.COMPANY)
        }

        // DEPT_HEAD & PROJECT_MANAGER: INVITE in Company
        savePermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.USER, ActionType.INVITE, ScopeType.COMPANY)
        savePermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.USER, ActionType.INVITE, ScopeType.COMPANY)
    }

    private fun initDepartmentPermissions() {
        val departmentResource = resourceRepository.findByName(ResourceType.DEPARTMENT)!!
        if (rolePermissionRepository.existsByResource(departmentResource)) return

        // MEMBER: READ in Department
        savePermissionIfNotExists(RoleType.MEMBER, ResourceType.DEPARTMENT, ActionType.READ, ScopeType.DEPARTMENT)

        // ADMIN_HEAD: Full Access + INVITE in Company
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE, ActionType.INVITE).forEach { action ->
            savePermissionIfNotExists(RoleType.ADMIN_HEAD, ResourceType.DEPARTMENT, action, ScopeType.COMPANY)
        }

        // ADMIN: READ, CREATE, UPDATE, INVITE in Company (ohne DELETE)
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.INVITE).forEach { action ->
            savePermissionIfNotExists(RoleType.ADMIN, ResourceType.DEPARTMENT, action, ScopeType.COMPANY)
        }

        // DEPT_HEAD: INVITE in Company
        savePermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.DEPARTMENT, ActionType.INVITE, ScopeType.COMPANY)
    }

    private fun initPermissionManagementPermissions() {
        // ADMIN_HEAD & ADMIN: Managing permissions for the system
        listOf(RoleType.ADMIN_HEAD, RoleType.ADMIN).forEach { role ->
            listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
                savePermissionIfNotExists(role, ResourceType.PERMISSION, action, ScopeType.COMPANY)
            }
        }
    }
}