package com.backend.todo_api.config

import com.backend.todo_api.data.entity.*
import com.backend.todo_api.data.repository.*
import com.backend.todo_api.model.*
import com.backend.todo_api.services.RolePermissionAdminService
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
    private val rolePermissionRepository: RolePermissionRepository,
    private val specializationRepository: DepartmentSpecializationRepository,
    private val rolePermissionService: RolePermissionAdminService
) : CommandLineRunner {

    @Transactional
    override fun run(vararg args: String) {
        initScopes()
        initResources()
        initActions()
        initRoles()
        initSpecializations()
        initPermissions()
        println("✅ [PermissionInitializer] RBAC- & Specialization-Initialdaten erfolgreich gepflegt!")
    }

    private fun initScopes() {
        ScopeType.entries.forEach { scopeType ->
            if (scopeRepository.findByName(scopeType) == null) {
                scopeRepository.save(ScopeEntity(name = scopeType, hierarchyLevel = scopeType.hierarchyLevel))
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
    }

    private fun initSpecializations() {
        DepartmentSpecializationType.entries.forEach { specType ->
            if (specializationRepository.findByName(specType) == null) {
                specializationRepository.save(
                    DepartmentSpecializationEntity(
                        name = specType,
                        description = specType.description
                    )
                )
            }
        }
    }

    private fun createRoleIfNotFound(roleType: RoleType, scope: ScopeEntity) {
        if (roleRepository.findByName(roleType) == null) {
            roleRepository.save(RoleEntity(name = roleType, scope = scope))
        }
    }

    // --- HELPER METHOD MIT SPECIALIZATION SUPPORT ---

//    private fun savePermissionIfNotExists(
//        roleType: RoleType,
//        resourceType: ResourceType,
//        actionType: ActionType,
//        scopeType: ScopeType,
//        specializationType: DepartmentSpecializationType? = null
//    ) {
//        val role = roleRepository.findByName(roleType)!!
//        val resource = resourceRepository.findByName(resourceType)!!
//        val action = actionRepository.findByName(actionType)!!
//        val scope = scopeRepository.findByName(scopeType)!!
//        val specialization = specializationType?.let { specializationRepository.findByName(it) }
//
//        val exists = rolePermissionRepository.existsByRoleAndResourceAndActionAndTargetScopeAndDepartmentSpecialization(
//            role, resource, action, scope, specialization
//        )
//
//        if (!exists) {
//            rolePermissionRepository.save(
//                RolePermissionEntity(
//                    role = role,
//                    resource = resource,
//                    action = action,
//                    targetScope = scope,
//                    departmentSpecialization = specialization
//                )
//            )
//            println("✨ [PermissionInit] Neue Permission angelegt: $roleType -> $resourceType [$actionType]")
//        }
//    }

    // --- PERMISSION MODULES ---

    private fun initPermissions() {
        initNotePermissions()
        initProjectPermissions()
        initTodoPermissions()
        initUserPermissions()
        initDepartmentPermissions()
        initPermissionManagementPermissions()
        initAdminSpecializationPermissions()
    }

    private fun initNotePermissions() {
        val noteResource = resourceRepository.findByName(ResourceType.NOTE)!!
 //       if (rolePermissionRepository.existsByResource(noteResource)) return

        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE, ActionType.EXECUTE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.OWNER, ResourceType.NOTE, action, ScopeType.RESOURCE)
        }

        // 2. Normale Abteilungs- & Projektmitglieder (DEPARTMENT / PROJECT Scope)
        listOf(ActionType.READ, ActionType.CREATE, ActionType.EXECUTE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.MEMBER, ResourceType.NOTE, action, ScopeType.DEPARTMENT)

            listOf(RoleType.DEVELOPER, RoleType.PROJECT_MANAGER).forEach { role ->
                rolePermissionService.createPermissionIfNotExists(role, ResourceType.NOTE, action, ScopeType.PROJECT)
            }
        }

        // 3. DEPARTMENT_HEAD: Rechte sowohl auf DEPARTMENT- als auch auf COMPANY-Ebene!
        listOf(ActionType.READ, ActionType.CREATE, ActionType.EXECUTE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.NOTE, action, ScopeType.DEPARTMENT)
            rolePermissionService.createPermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.NOTE, action, ScopeType.COMPANY)
        }

        // 4. ADMIN-Spezialisierung (Sonderaktionen PROMOTE & REVERT)
        listOf(ActionType.PROMOTE, ActionType.REVERT).forEach { action ->
            listOf(RoleType.MEMBER, RoleType.DEPARTMENT_HEAD).forEach { role ->
                rolePermissionService.createPermissionIfNotExists(
                    role, ResourceType.NOTE, action, ScopeType.COMPANY,
                    DepartmentSpecializationType.ADMIN
                )
            }
        }
    }

    private fun initProjectPermissions() {
        val projectResource = resourceRepository.findByName(ResourceType.PROJECT)!!
//        if (rolePermissionRepository.existsByResource(projectResource)) return

        listOf(ActionType.READ, ActionType.UPDATE, ActionType.EXECUTE, ActionType.CREATE, ActionType.INVITE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.OWNER, ResourceType.PROJECT, action, ScopeType.RESOURCE)
        }

        listOf(ActionType.CREATE, ActionType.READ, ActionType.UPDATE, ActionType.EXECUTE, ActionType.INVITE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.PROJECT, action, ScopeType.PROJECT)
            rolePermissionService.createPermissionIfNotExists(RoleType.DEVELOPER, ResourceType.PROJECT, action, ScopeType.PROJECT)
        }

        listOf(ActionType.READ, ActionType.DELETE, ActionType.CREATE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.PROJECT, action, ScopeType.DEPARTMENT)
        }

        listOf(ActionType.READ, ActionType.CREATE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.MEMBER, ResourceType.PROJECT, action, ScopeType.DEPARTMENT)
        }
    }

    private fun initTodoPermissions() {
        val todoResource = resourceRepository.findByName(ResourceType.TODO)!!
        if (rolePermissionRepository.existsByResource(todoResource)) return

        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.OWNER, ResourceType.TODO, action, ScopeType.RESOURCE)
        }

        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.MEMBER, ResourceType.TODO, action, ScopeType.PROJECT)
            rolePermissionService.createPermissionIfNotExists(RoleType.DEVELOPER, ResourceType.TODO, action, ScopeType.PROJECT)
        }

        listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.TODO, action, ScopeType.PROJECT)
        }
    }

    private fun initUserPermissions() {
        listOf(ActionType.READ, ActionType.UPDATE, ActionType.DELETE).forEach { action ->
            rolePermissionService.createPermissionIfNotExists(RoleType.OWNER, ResourceType.USER, action, ScopeType.RESOURCE)
        }

        listOf(RoleType.MEMBER, RoleType.DEPARTMENT_HEAD, RoleType.DEVELOPER).forEach { role ->
            rolePermissionService.createPermissionIfNotExists(role, ResourceType.USER, ActionType.READ, ScopeType.DEPARTMENT)
        }

        rolePermissionService.createPermissionIfNotExists(RoleType.DEVELOPER, ResourceType.USER, ActionType.READ, ScopeType.PROJECT)
        rolePermissionService.createPermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.USER, ActionType.READ, ScopeType.PROJECT)
        rolePermissionService.createPermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.USER, ActionType.INVITE, ScopeType.COMPANY)
        rolePermissionService.createPermissionIfNotExists(RoleType.PROJECT_MANAGER, ResourceType.USER, ActionType.INVITE, ScopeType.COMPANY)
    }

    private fun initDepartmentPermissions() {
        rolePermissionService.createPermissionIfNotExists(RoleType.MEMBER, ResourceType.DEPARTMENT, ActionType.READ, ScopeType.DEPARTMENT)
        rolePermissionService.createPermissionIfNotExists(RoleType.DEPARTMENT_HEAD, ResourceType.DEPARTMENT, ActionType.INVITE, ScopeType.COMPANY)
    }

    private fun initPermissionManagementPermissions() {
        // Allgemeine administrative Basis-Rechte falls benötigt
    }

    // --- NEU: SPEZIALISIERUNGS-RECHTE (z.B. MEMBER in ADMIN-Abteilung) ---
    private fun initAdminSpecializationPermissions() {
        val adminResources = listOf(ResourceType.USER, ResourceType.DEPARTMENT, ResourceType.PERMISSION)

        // Jedes Abteilungsmitglied (MEMBER oder DEPARTMENT_HEAD) der ADMIN-Spezialisierung bekommt globale COMPANY-Rechte!
        listOf(RoleType.MEMBER, RoleType.DEPARTMENT_HEAD).forEach { role ->
            adminResources.forEach { resource ->
                listOf(ActionType.READ, ActionType.CREATE, ActionType.UPDATE, ActionType.DELETE, ActionType.INVITE).forEach { action ->
                    rolePermissionService.createPermissionIfNotExists(
                        roleType = role,
                        resourceType = resource,
                        actionType = action,
                        scopeType = ScopeType.COMPANY,
                        specializationType = DepartmentSpecializationType.ADMIN
                    )
                }
            }
        }
    }
}