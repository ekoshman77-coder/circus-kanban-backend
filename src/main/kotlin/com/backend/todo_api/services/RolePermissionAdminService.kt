package com.backend.todo_api.services

import com.backend.todo_api.data.entity.ActionEntity
import com.backend.todo_api.data.entity.DepartmentSpecializationEntity
import com.backend.todo_api.data.entity.ResourceEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.repository.*
import com.backend.todo_api.dto.*
import com.backend.todo_api.data.entity.RolePermissionEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.PermissionAlreadyExistsException
import com.backend.todo_api.exceptions.PermissionNotFoundException
import com.backend.todo_api.model.*
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RolePermissionAdminService(
    private val rolePermissionRepository: RolePermissionRepository,
    private val roleRepository: RoleRepository,
    private val actionRepository: ActionRepository,
    private val resourceRepository: ResourceRepository,
    private val scopeRepository: ScopeRepository,
    private val specializationRepository: DepartmentSpecializationRepository,
    private val permissionService: PermissionService,
    private val userContextResolver: UserContextResolver
) {

    fun RolePermissionEntity.toDto(): RolePermissionResponseDto {
        return RolePermissionResponseDto(
            id = this.id,
            role = this.role.name.name,
            resource = this.resource.name.name,
            action = this.action.name.name,
            targetScope = this.targetScope.name.name,
            specialization = this.departmentSpecialization?.name?.name
        )
    }

    // 2. Create DTO -> Entity
    fun CreateRolePermissionDto.toEntity(
        role: RoleEntity,
        resource: ResourceEntity,
        action: ActionEntity,
        targetScope: ScopeEntity,
        specialization: DepartmentSpecializationEntity?
    ): RolePermissionEntity {
        return RolePermissionEntity(
            role = role,
            resource = resource,
            action = action,
            targetScope = targetScope,
            departmentSpecialization = specialization
        )
    }

    /** 1. Alle Stammdaten für das Admin-Board liefern */
    @Transactional(readOnly = true)
    fun getMasterData(): MasterDataResponseDto {
        return MasterDataResponseDto(
            departmentScopes = ScopeType.entries.filter { it.isDepartmentSelectable }.map { it.name },
            allScopes = ScopeType.entries.map { it.name },
            departmentRoles = RoleType.entries.filter { it.isDepartmentRole }.map { it.name },
            projectRoles = RoleType.entries.filter { it.isProjectRole }.map { it.name },
            otherRoles = RoleType.entries.filter { !it.isDepartmentRole && !it.isProjectRole }.map { it.name },
            resources = ResourceType.entries.map { it.name },
            actions = ActionType.entries.map { it.name },
            specializations = DepartmentSpecializationType.entries.map { it.name }
        )
    }

    private fun verifyAdminPermission(userId: String, requiredAction: ActionType) {
        val userContexts = userContextResolver.resolveContexts(userId)

        val hasPermission = permissionService.hasPermission(
            userContexts = userContexts,
            resource = PermissionSecurityResource(),
            action = requiredAction,
        )

        if (!hasPermission) {
            throw ActionForbiddenException("Du hast keine Berechtigung, Berechtigungen zu verwalten ($requiredAction)!")
        }
    }

    /** 2. Alle aktuell in der DB gespeicherten Berechtigungen abrufen */
    @Transactional(readOnly = true)
    fun getAllRolePermissions(userId: String): List<RolePermissionResponseDto> {
        verifyAdminPermission(userId, ActionType.READ)

        return rolePermissionRepository.findAll().map { perm ->
            perm.toDto()
        }
    }

    /** 3. Den Scope einer bestehenden Regel anpassen */
    @Transactional
    fun updatePermissionScope(userId: String, dto: UpdateRolePermissionDto): RolePermissionResponseDto {
        verifyAdminPermission(userId, ActionType.UPDATE)

        val perm = rolePermissionRepository.findById(dto.id)
            .orElseThrow { PermissionNotFoundException("Permission mit ID ${dto.id} nicht gefunden.") }

        val newScopeType = ScopeType.valueOf(dto.targetScope)
        val newScopeEntity = newScopeType.toEntity(scopeRepository)

        perm.targetScope = newScopeEntity
        val saved = rolePermissionRepository.save(perm)

        return saved.toDto()
    }

    /** 4. Eine neue Berechtigung anlegen */
    @Transactional
    fun createPermission(userId: String, dto: CreateRolePermissionDto): RolePermissionResponseDto {
        verifyAdminPermission(userId, ActionType.CREATE)

        // 1. Strings in Enum-Typen auflösen
        val roleType = RoleType.valueOf(dto.role)
        val resourceType = ResourceType.valueOf(dto.resource)
        val actionType = ActionType.valueOf(dto.action)
        val targetScopeType = ScopeType.valueOf(dto.targetScope)
        val specType = dto.specialization?.takeIf { it.isNotBlank() }?.let { DepartmentSpecializationType.valueOf(it) }

        // 2. Über die Enum Extension-Funktionen sauber die Entities aus der DB laden
        val roleEntity = roleType.toEntity(roleRepository)
        val resourceEntity = resourceType.toEntity(resourceRepository)
        val actionEntity = actionType.toEntity(actionRepository)
        val scopeEntity = targetScopeType.toEntity(scopeRepository)
        val specEntity = specType?.toEntity(specializationRepository)

        // 3. Duplikats-Prüfung
        if (rolePermissionRepository.existsByRoleAndResourceAndActionAndTargetScopeAndDepartmentSpecialization(
                roleEntity, resourceEntity, actionEntity, scopeEntity, specEntity
            )
        ) {
            throw PermissionAlreadyExistsException(
                "Eine Regel für '$roleType' + '$resourceType' + '$actionType' im Scope '$targetScopeType' (Spezialisierung: ${dto.specialization}) existiert bereits!"
            )
        }

        // 4. Entity erstellen & speichern
        val newEntity = dto.toEntity(
            role = roleEntity,
            resource = resourceEntity,
            action = actionEntity,
            targetScope = scopeEntity,
            specialization = specEntity
        )

        return rolePermissionRepository.save(newEntity).toDto()
    }

    @Transactional
    fun deletePermission(userid: String, id: String) {
        verifyAdminPermission(userid, ActionType.DELETE)
        val perm = rolePermissionRepository.findById(id)
            .orElseThrow { PermissionNotFoundException("Permission mit ID $id existiert nicht.") }

        checkSelfLockoutAttempt(perm)
        rolePermissionRepository.delete(perm)
    }

    @Transactional
    fun createBatchPermissions(
        userId: String,
        dto: BatchCreateRolePermissionDto
         // 👈 UserContext des aktuellen Aufrufers
    ): List<RolePermissionResponseDto> {

        // 🔒 1. Einmalige Sicherheitsprüfung für die gesamte Gruppe!
        verifyAdminPermission(userId, ActionType.CREATE)

        val createdPermissions = mutableListOf<RolePermissionResponseDto>()

        // 2. Erstellen aller gewünschten Kombos
        for (role in dto.roles) {
            for (action in dto.actions) {
                val entity = createPermissionIfNotExists(
                    roleType = role,
                    resourceType = dto.resource,
                    actionType = action,
                    scopeType = dto.scope,
                    specializationType = dto.specialization
                )
                createdPermissions.add(entity.toDto())
            }
        }
        return createdPermissions
    }


    private fun checkSelfLockoutAttempt(perm: RolePermissionEntity) {
        // 🔒 Systemschutz: Admin-Spezialisierungs-Rechte dürfen im UI nicht gelöscht werden!
        if (perm.departmentSpecialization?.name == DepartmentSpecializationType.ADMIN
            && perm.resource.name == ResourceType.PERMISSION
        ) {
            throw ActionForbiddenException("System-Berechtigungen für die ADMIN-Spezialisierung dürfen nicht gelöscht werden!")
        }
    }

    @Transactional
    fun createPermissionIfNotExists(
        roleType: RoleType,
        resourceType: ResourceType,
        actionType: ActionType,
        scopeType: ScopeType,
        specializationType: DepartmentSpecializationType? = null
    ): RolePermissionEntity {

        val role = roleRepository.findByName(roleType)
            ?: throw EntityNotFoundException("Role $roleType nicht gefunden")
        val resource = resourceRepository.findByName(resourceType)
            ?: throw EntityNotFoundException("Resource $resourceType nicht gefunden")
        val action = actionRepository.findByName(actionType)
            ?: throw EntityNotFoundException("Action $actionType nicht gefunden")
        val scope = scopeRepository.findByName(scopeType)
            ?: throw EntityNotFoundException("Scope $scopeType nicht gefunden")

        val specialization = specializationType?.let {
            specializationRepository.findByName(it)
                ?: throw EntityNotFoundException("Specialization $it nicht gefunden")
        }

        // Prüfen, ob exakt diese Berechtigung schon existiert
        val existing = rolePermissionRepository.findByRoleAndResourceAndActionAndTargetScopeAndDepartmentSpecialization(
            role, resource, action, scope, specialization
        ) ?: null // oder direkt Nullable im Repository

        return if (existing != null) {
            existing
        } else {
            rolePermissionRepository.save(
                RolePermissionEntity(
                    role = role,
                    resource = resource,
                    action = action,
                    targetScope = scope,
                    departmentSpecialization = specialization ?: null
                )
            )
        }
    }
}