package com.backend.todo_api.services

import com.backend.todo_api.data.entity.DepartmentSpecializationEntity
import com.backend.todo_api.data.entity.DraftChainEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.repository.DraftChainRepository
import com.backend.todo_api.dto.DelegateDraftDto
import com.backend.todo_api.dto.DelegationTargetDto
import com.backend.todo_api.model.DepartmentSpecializationType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.UserContext
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import java.util.Optional

class DraftBackendServiceTest {

    private val draftChainRepository: DraftChainRepository = mockk()
    private val userContextResolver: UserContextResolver = mockk()

    private lateinit var draftService: DraftBackendService

    @BeforeEach
    fun setUp() {
        draftService = DraftBackendService(
            draftChainRepository = draftChainRepository,
            userContextResolver = userContextResolver
        )
    }

    @Test
    fun `getAssignedDraftsForCurrentUser - sollte Liste von Drafts zurueckgeben bei gelösten Contexts`() {
        // 1. Contexts mit echten Model-Objekten erstellen (keine komplexen Mocks!)
        val deptScopeEntity = ScopeEntity(id = "scope-dept", name = ScopeType.DEPARTMENT)
        val companyScopeEntity = ScopeEntity(id = "scope-comp", name = ScopeType.COMPANY)
        val roleEntity = RoleEntity(id = "role-1", name = RoleType.DEVELOPER, scope = deptScopeEntity)

        val adminSpecEntity = DepartmentSpecializationEntity(
            id = "spec-admin",
            name = DepartmentSpecializationType.ADMIN
        )

        val userContexts = listOf(
            UserContext(
                scope = deptScopeEntity,
                scopeInstanceId = "dept-123",
                role = roleEntity
            ),
            UserContext(
                scope = companyScopeEntity,
                scopeInstanceId = null,
                role = roleEntity,
                specialization = adminSpecEntity
            )
        )

        // 2. Mock-Verhalten definieren
        every { userContextResolver.resolveContexts("user-123") } returns userContexts
        every { draftChainRepository.findAll(any< Specification< DraftChainEntity>>(), any< Sort>()) } returns emptyList()

        // 3. Ausführung
        val result = draftService.getAssignedDraftsForCurrentUser("user-123")

        // 4. Überprüfung
        assertNotNull(result)
        assertTrue(result.isEmpty())
        verify { userContextResolver.resolveContexts("user-123") }
        verify { draftChainRepository.findAll(any< Specification< DraftChainEntity>>(), any<Sort>()) }
    }

    @Test
    fun `delegateDraft - sollte DTO konvertieren und Entity mit delegatedByUserId speichern`() {
        val dto = DelegateDraftDto(
            id = "chain-1",
            title = "Test Draft",
            target = DelegationTargetDto(type = "USER", id = "target-user-999"),
            createdAt = System.currentTimeMillis(),
            createdByUserId = "user-123",
            lastErrorReason = "HTTP 500",
            lastErrorCode = 500
        )

        val entitySlot = slot< DraftChainEntity>()
        every { draftChainRepository.save(capture(entitySlot)) } answers { firstArg() }

        draftService.delegateDraft("chain-1", dto, "user-123")

        verify { draftChainRepository.save(any()) }
        assertTrue(entitySlot.isCaptured)
        assertEquals("chain-1", entitySlot.captured.id)
        assertEquals("user-123", entitySlot.captured.delegatedByUserId)
    }

    @Test
    fun `deleteDraft - sollte Draft loeschen wenn in DB vorhanden`() {
        val entity = DraftChainEntity(id = "chain-1")

        every { draftChainRepository.findById("chain-1") } returns Optional.of(entity)
        every { draftChainRepository.delete(entity) } just Runs

        draftService.deleteDraft("chain-1", "user-123")

        verify { draftChainRepository.findById("chain-1") }
        verify { draftChainRepository.delete(entity) }
    }
}