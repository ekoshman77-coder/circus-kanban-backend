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
    EXECUTE,
    INVITE
}

fun ActionType.toEntity(actionRepository: ActionRepository): ActionEntity {
    return actionRepository.findByName(this)
        ?: throw IllegalStateException("Kritischer Fehler: Action $this existiert nicht in der Datenbank!")
}

// 3. Alle verfügbaren Hierarchie-Scopes
enum class ScopeType(
    val hierarchyLevel: Int,
    val isDepartmentSelectable: Boolean,
    val description: String
) {
    RESOURCE(10, isDepartmentSelectable = false, "Nur die eigene Ressource"),
    PROJECT(20, isDepartmentSelectable = false, "Projekt-Ebene"),
    DEPARTMENT(30, isDepartmentSelectable = true, "Abteilungs-Ebene"),
    COMPANY(40, isDepartmentSelectable = true, "Firmenweit / Global")
}

fun ScopeType.toEntity(scopeRepository: ScopeRepository): ScopeEntity{
    return scopeRepository.findByName(this)
        ?: throw IllegalStateException("Kritischer Fehler: Scope $this existiert nicht in der Datenbank!")
}

// 4. Alle verfügbaren Rollen
enum class RoleType(
    val isProjectRole: Boolean,
    val isDepartmentRole: Boolean,
    val description: String
) {
    // 🏢 Organisatorische Abteilungsrollen
    ADMIN_HEAD(isProjectRole = false, isDepartmentRole = true, "Super-Administrator (Volle Systemkontrolle)"),
    ADMIN(isProjectRole = false, isDepartmentRole = true, "Systemweiter Administrator"),
    DEPARTMENT_HEAD(isProjectRole = false, isDepartmentRole = true, "Abteilungsleiter"),
    MEMBER(isProjectRole = false, isDepartmentRole = true, "Standard-Abteilungsmitglied"),

    // 📂 Projekt- & Teamrollen
    OWNER(isProjectRole = true, isDepartmentRole = false, "Projekteigentümer"),
    PROJECT_MANAGER(isProjectRole = true, isDepartmentRole = false, "Projektleiter"),
    DEVELOPER(isProjectRole = true, isDepartmentRole = false, "Entwickler / Teammitglied")
}

// Erlaubt den Aufruf: RoleType.ADMIN.toEntity(roleRepository)
fun RoleType.toEntity(roleRepository: RoleRepository): RoleEntity {
    return roleRepository.findByName(this)
        ?: throw IllegalStateException("Kritischer Fehler: Rolle $this existiert nicht in der Datenbank!")
}