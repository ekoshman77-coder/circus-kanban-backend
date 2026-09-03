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
        // 1. Erst direkt mit den Enums prüfen
        val exists = rolePermissionRepository.existsByRoleNameAndResourceNameAndActionNameAndTargetScopeName(
            roleType, resourceType, actionType, scopeType
        )

        // 2. Nur wenn es fehlt, laden wir die Entities und speichern
        if (!exists) {
            val role = roleRepository.findByName(roleType)!!
            val resource = resourceRepository.findByName(resourceType)!!
            val action = actionRepository.findByName(actionType)!!
            val scope = scopeRepository.findByName(scopeType)!!

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

        savePermissionIfNotExists(RoleType.DEVELOPER, ResourceType.NOTE, ActionType.READ, ScopeType.PROJECT)

        // ADMIN & ADMIN_HEAD: READ in Company
        savePermissionIfNotExists(RoleType.ADMIN, ResourceType.NOTE, ActionType.READ, ScopeType.COMPANY)
        savePermissionIfNotExists(RoleType.ADMIN_HEAD, ResourceType.NOTE, ActionType.READ, ScopeType.COMPANY)

        // ADMIN & ADMIN_HEAD: READ, PROMOTE, REVERT in Company
        listOf(ActionType.READ, ActionType.PROMOTE, ActionType.REVERT).forEach { action ->
            savePermissionIfNotExists(RoleType.ADMIN, ResourceType.NOTE, action, ScopeType.COMPANY)
            savePermissionIfNotExists(RoleType.ADMIN_HEAD, ResourceType.NOTE, action, ScopeType.COMPANY)
        }

        listOf(RoleType.ADMIN, RoleType.ADMIN_HEAD).forEach { role ->
            savePermissionIfNotExists(role, ResourceType.NOTE, ActionType.EXECUTE, ScopeType.COMPANY)
        }
        listOf(RoleType.MEMBER, RoleType.DEPARTMENT_HEAD).forEach { role ->
            savePermissionIfNotExists(role, ResourceType.NOTE, ActionType.EXECUTE, ScopeType.DEPARTMENT)
        }
    }

    private fun initProjectPermissions() {
        val projectResource = resourceRepository.findByName(ResourceType.PROJECT)!!
        if (rolePermissionRepository.existsByResource(projectResource)) return

        // OWNER: Volle Bearbeitungsrechte auf eigene Resource (ohne DELETE)
        listOf(ActionType.READ, ActionType.UPDATE, ActionType.EXECUTE, ActionType.CREATE, ActionType.INVITE).forEach { action ->
            savePermissionIfNotExists(RoleType.OWNER, ResourceType.PROJECT, action, ScopeType.RESOURCE)
        }

        // PROJECT_MANAGER & DEVELOPER: Projektarbeit im PROJECT-Scope
        listOf(ActionType.CREATE, ActionType.READ, ActionType.UPDATE, ActionType.EXECUTE, ActionType.INVITE).forEach { action ->
            savePermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.PROJECT, action, ScopeType.PROJECT)
            savePermissionIfNotExists(RoleType.DEVELOPER, ResourceType.PROJECT, action, ScopeType.PROJECT)
        }

        // DEPARTMENT_HEAD: Verwaltung & Löschen auf Abteilungs-Ebene
        listOf(ActionType.READ, ActionType.DELETE, ActionType.CREATE).forEach { action ->
            savePermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.PROJECT, action, ScopeType.DEPARTMENT)
        }

        // MEMBER: Lesen & Erstellen in der Abteilung
        listOf(ActionType.READ, ActionType.CREATE).forEach { action ->
            savePermissionIfNotExists(RoleType.MEMBER, ResourceType.PROJECT, action, ScopeType.DEPARTMENT)
        }

        // ADMIN & ADMIN_HEAD: Governance & Löschen auf Firmen-Ebene
        listOf(ActionType.READ, ActionType.CREATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.ADMIN, ResourceType.PROJECT, action, ScopeType.COMPANY)
            savePermissionIfNotExists(RoleType.ADMIN_HEAD, ResourceType.PROJECT, action, ScopeType.COMPANY)
        }
    }

    private fun initTodoPermissions() {
        val todoResource = resourceRepository.findByName(ResourceType.TODO)!!
        if (rolePermissionRepository.existsByResource(todoResource)) return

        // 1. OWNER: Voller Zugriff nur auf die eigenen, privaten Todos (RESOURCE-Scope)
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.OWNER, ResourceType.TODO, action, ScopeType.RESOURCE)
        }

        // 2. DEVELOPER & MEMBER: Projekt-Todos lesen, erstellen & bearbeiten (PROJECT-Scope)
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE).forEach { action ->
            savePermissionIfNotExists(RoleType.MEMBER, ResourceType.TODO, action, ScopeType.PROJECT)
            savePermissionIfNotExists(RoleType.DEVELOPER, ResourceType.TODO, action, ScopeType.PROJECT)
        }

        // 3. PROJECT_MANAGER: Voller Zugriff auf Projekt-Todos inkl. Löschen (PROJECT-Scope)
        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.TODO, action, ScopeType.PROJECT)
        }
    }

    private fun initUserPermissions() {
        // OWNER: READ, UPDATE, DELETE auf die eigene User-Ressource
        listOf(ActionType.READ, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            savePermissionIfNotExists(RoleType.OWNER, ResourceType.USER, action, ScopeType.RESOURCE)
        }

        // MEMBER, DEPT_HEAD UND DEVELOPER: Dürfen alle Kollegen in der eigenen Abteilung lesen!
        listOf(RoleType.MEMBER, RoleType.DEPARTMENT_HEAD, RoleType.DEVELOPER).forEach { role ->
            savePermissionIfNotExists(role, ResourceType.USER, ActionType.READ, ScopeType.DEPARTMENT)
        }

        // DEVELOPER & PROJECT_MANAGER: READ in Project (auch für abteilungsfremde Kollegen!)
        savePermissionIfNotExists(RoleType.DEVELOPER, ResourceType.USER, ActionType.READ, ScopeType.PROJECT)
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