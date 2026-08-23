package com.backend.todo_api.services

import com.backend.todo_api.data.repository.*
import com.backend.todo_api.dto.*
import com.backend.todo_api.data.entity.RolePermissionEntity
import com.backend.todo_api.exceptions.PermissionAlreadyExistsException
import com.backend.todo_api.exceptions.PermissionNotFoundException
import com.backend.todo_api.model.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RolePermissionAdminService(
    private val rolePermissionRepository: RolePermissionRepository,
    private val roleRepository: RoleRepository,
    private val actionRepository: ActionRepository,
    private val resourceRepository: ResourceRepository,
    private val scopeRepository: ScopeRepository
) {

    fun RolePermissionEntity.toDto(): RolePermissionResponseDto {
        return RolePermissionResponseDto(
            id = this.id,
            role = this.role.name.name,
            resource = this.resource.name.name,
            action = this.action.name.name,
            targetScope = this.targetScope.name.name
        )
    }

    // 2. Create DTO -> Entity
    fun CreateRolePermissionDto.toEntity(
        roleRepository: RoleRepository,
        resourceRepository: ResourceRepository,
        actionRepository: ActionRepository,
        scopeRepository: ScopeRepository
    ): RolePermissionEntity {
        return RolePermissionEntity(
            role = RoleType.valueOf(this.role).toEntity(roleRepository),
            resource = ResourceType.valueOf(this.resource).toEntity(resourceRepository),
            action = ActionType.valueOf(this.action).toEntity(actionRepository),
            targetScope = ScopeType.valueOf(this.targetScope).toEntity(scopeRepository)
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
            actions = ActionType.entries.map { it.name }
        )
    }

    /** 2. Alle aktuell in der DB gespeicherten Berechtigungen abrufen */
    @Transactional(readOnly = true)
    fun getAllRolePermissions(): List<RolePermissionResponseDto> {
        return rolePermissionRepository.findAll().map { perm ->
            perm.toDto()
        }
    }

    /** 3. Den Scope einer bestehenden Regel anpassen */
    @Transactional
    fun updatePermissionScope(dto: UpdateRolePermissionDto): RolePermissionResponseDto {
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
    fun createPermission(dto: CreateRolePermissionDto): RolePermissionResponseDto {
        val roleType = RoleType.valueOf(dto.role)
        val resourceType = ResourceType.valueOf(dto.resource)
        val actionType = ActionType.valueOf(dto.action)
        val targetScope = ScopeType.valueOf((dto.targetScope))

        if (rolePermissionRepository.existsByRoleNameAndResourceNameAndActionNameAndTargetScopeName(roleType, resourceType, actionType, targetScope)) {
            throw PermissionAlreadyExistsException(
                "Eine Regel für '$roleType' + '$resourceType' + '$actionType' im Scope '$targetScope' existiert bereits!"
            )
        }
        val newEntity = dto.toEntity(
            roleRepository,
            resourceRepository,
            actionRepository,
            scopeRepository
        )
        return rolePermissionRepository.save(newEntity).toDto()
    }

    @Transactional
    fun deletePermission(id: String) {
        if (!rolePermissionRepository.existsById(id)) {
            throw PermissionNotFoundException("Permission mit ID $id existiert nicht.")
        }
        rolePermissionRepository.deleteById(id)
    }
}