package com.backend.todo_api.services

import com.backend.todo_api.dto.MasterDataResponseDto
import com.backend.todo_api.model.enums.ActionType
import com.backend.todo_api.model.enums.DepartmentSpecializationType
import com.backend.todo_api.model.enums.PatternTypeEnum
import com.backend.todo_api.model.enums.ResourceType
import com.backend.todo_api.model.enums.RoleType
import com.backend.todo_api.model.enums.ScopeType
import com.backend.todo_api.model.enums.TodoGroupChoice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MasterDataService {
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
            specializations = DepartmentSpecializationType.entries.map { it.name },
            patternTypes = PatternTypeEnum.entries.map { it.name },
            todoGroupChoices = TodoGroupChoice.entries.map { it.name }
        )
    }

}