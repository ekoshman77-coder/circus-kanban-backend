package com.backend.todo_api.services

import com.backend.todo_api.data.entity.*
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class UserContextResolverTest {

    private val userRepository: UserRepository = mockk()
    private val roleRepository: RoleRepository = mockk()
    private val departmentRepository: DepartmentRepository = mockk()
    private val scopeRepository: ScopeRepository = mockk()

    private lateinit var resolver: UserContextResolver

    private val userId = "user-123"
    private val deptScope = ScopeEntity(id = "s-dept", name = ScopeType.DEPARTMENT)
    private val resourceScope = ScopeEntity(id = "s-res", name = ScopeType.RESOURCE)

    private val ownerRole = RoleEntity(id = "r-owner", name = RoleType.OWNER, scope = resourceScope)
    private val memberRole = RoleEntity(id = "r-member", name = RoleType.MEMBER, scope = deptScope)

    @BeforeEach
    fun setUp() {
        resolver = UserContextResolver(
            roleRepository = roleRepository,
            scopeRepository = scopeRepository,
            departmentRepository = departmentRepository,
            userRepository = userRepository
        )
    }

    @Test
    fun `resolveContexts sollte leere Liste liefern, wenn User nicht geapprovt ist`() {
        val unapprovedUser = UserEntity(id = userId, isApproved = false)

        val contexts = resolver.resolveContexts(unapprovedUser)

        assertTrue(contexts.isEmpty())
    }

    @Test
    fun `resolveContexts sollte RESOURCE und DEPARTMENT Kontexte auflösen`() {
        val department = DepartmentEntity(id = "dept-1")
        val approvedUser = UserEntity(
            id = userId,
            isApproved = true,
            departmentId = "dept-1",
            departmentRole = memberRole
        )

        every { roleRepository.findByName(RoleType.OWNER) } returns ownerRole
        every { scopeRepository.findByName(ScopeType.DEPARTMENT) } returns deptScope
        every { departmentRepository.findById("dept-1") } returns Optional.of(department)

        val contexts = resolver.resolveContexts(approvedUser)

        assertEquals(2, contexts.size)

        // 1. Resource/Owner-Kontext
        val ownerContext = contexts.find { it.role.name == RoleType.OWNER }
        assertNotNull(ownerContext)
        assertEquals(userId, ownerContext?.scopeInstanceId)

        // 2. Abteilungs-Kontext
        val deptContext = contexts.find { it.role.name == RoleType.MEMBER }
        assertNotNull(deptContext)
        assertEquals("dept-1", deptContext?.scopeInstanceId)
    }

    @Test
    fun `resolveContexts mit userId sollte User aus UserRepository laden`() {
        val approvedUser = UserEntity(
            id = userId,
            isApproved = true
        )

        every { userRepository.findById(userId) } returns Optional.of(approvedUser)
        every { roleRepository.findByName(RoleType.OWNER) } returns ownerRole

        val contexts = resolver.resolveContexts(userId)

        assertEquals(1, contexts.size)
        assertEquals(RoleType.OWNER, contexts.first().role.name)
    }
}