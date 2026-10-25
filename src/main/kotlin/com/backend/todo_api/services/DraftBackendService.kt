package com.backend.todo_api.services

import com.backend.todo_api.data.entity.DraftChainEntity
import com.backend.todo_api.data.repository.DraftChainRepository
import com.backend.todo_api.data.repository.specifications.DraftChainSpecifications
import com.backend.todo_api.dto.DelegateDraftDto
import com.backend.todo_api.dto.DraftChainDto
import com.backend.todo_api.mapper.toDto
import com.backend.todo_api.mapper.toEntity
import com.backend.todo_api.model.DepartmentSpecializationType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.UserContext
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service

@Service
class DraftBackendService(
    private val draftChainRepository: DraftChainRepository,
    private val userContextResolver: UserContextResolver
) {

    /**
     * 1. GET /api/drafts
     */
    fun getAssignedDraftsForCurrentUser(currentUserId: String): List<DraftChainDto> {
        val contexts = userContextResolver.resolveContexts(currentUserId)

        val userIds = listOf(currentUserId)

        val departmentIds = contexts
            .filter { it.scope.name == ScopeType.DEPARTMENT && it.scopeInstanceId != null }
            .mapNotNull { it.scopeInstanceId }

        val projectIds = contexts
            .filter { it.scope.name == ScopeType.PROJECT && it.scopeInstanceId != null }
            .mapNotNull { it.scopeInstanceId }

        // Admin-Status basiert rein auf der Department-Spezialisierung ADMIN
        val isAdmin: Boolean = contexts.any { context ->
            context.specialization?.name == DepartmentSpecializationType.ADMIN
        }

        val spec = DraftChainSpecifications.isAssignedTo(
            userIds = userIds,
            departmentIds = departmentIds,
            projectIds = projectIds,
            isAdmin = isAdmin // Wenn true, schließt die Specification zusätzlich targetType = "ADMINS" ein
        )

        val sort = Sort.by(Sort.Direction.DESC, "createdAt")
        val entities = draftChainRepository.findAll(spec, sort)

        return entities.map { entity -> entity.toDto() }
    }

    /**
     * 2. POST /api/drafts/{id}/delegate
     */
    fun delegateDraft(chainId: String, dto: DelegateDraftDto, currentUserId: String) {
        val entity: DraftChainEntity = dto.toEntity(currentUserId)
        draftChainRepository.save(entity)
    }

    /**
     * 3. DELETE /api/drafts/{id}
     */
    fun deleteDraft(chainId: String, currentUserId: String) {
        val draft = draftChainRepository.findById(chainId).orElse(null) ?: return
        draftChainRepository.delete(draft)
    }
}