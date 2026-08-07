package com.backend.todo_api.model

import com.backend.todo_api.data.entity.ActionEntity
import com.backend.todo_api.data.entity.ResourceEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.repository.ActionRepository
import com.backend.todo_api.data.repository.ResourceRepository
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.data.repository.ScopeRepository

// 1. Alle verfügbaren Ressourcen im System
enum class ResourceType {
    NOTE,
    TODO,
    PROJECT,
    USER,
    DEPARTMENT
}

fun ResourceType.toEntity(resourceRepository: ResourceRepository): ResourceEntity {
    return resourceRepository.findByName(this)
        ?: throw IllegalStateException("Kritischer Fehler: Resource $this existiert nicht in der Datenbank!")
}

// 2. Alle verfügbaren Aktionen
enum class ActionType {
    READ,
    CREATE,
    UPDATE,
    DELETE,
    EXECUTE
}

fun ActionType.toEntity(actionRepository: ActionRepository): ActionEntity {
    return actionRepository.findByName(this)
        ?: throw IllegalStateException("Kritischer Fehler: Action $this existiert nicht in der Datenbank!")
}

// 3. Alle verfügbaren Hierarchie-Scopes
enum class ScopeType(val hierarchyLevel: Int) {
    RESOURCE(10),  // Nur das eigene Objekt (Instanz-Level)
    PROJECT(20),   // Projekt-Level
    DEPARTMENT(30),// Abteilungs-Level
    COMPANY(40)    // Firmenweit / Global
}

fun ScopeType.toEntity(scopeRepository: ScopeRepository): ScopeEntity{
    return scopeRepository.findByName(this)
        ?: throw IllegalStateException("Kritischer Fehler: Scope $this existiert nicht in der Datenbank!")
}

// 4. Alle verfügbaren Rollen
enum class RoleType {
    OWNER,
    MEMBER,
    DEVELOPER,
    PROJECT_MANAGER,
    DEPARTMENT_HEAD,
    ADMIN
}

// Erlaubt den Aufruf: RoleType.ADMIN.toEntity(roleRepository)
fun RoleType.toEntity(roleRepository: RoleRepository): RoleEntity {
    return roleRepository.findByName(this)
        ?: throw IllegalStateException("Kritischer Fehler: Rolle $this existiert nicht in der Datenbank!")
}