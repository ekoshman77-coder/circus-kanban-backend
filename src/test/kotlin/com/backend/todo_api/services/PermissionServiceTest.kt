package com.backend.todo_api.services

import com.backend.todo_api.data.entity.*
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.RolePermissionRepository
import com.backend.todo_api.model.*
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PermissionServiceTest {

    private val rolePermissionRepository: RolePermissionRepository = mockk()
    private val departmentRepository: DepartmentRepository = mockk()

    private lateinit var permissionService: PermissionService

    // Hilfs-Entities für sauberere Tests
    private val companyScope = ScopeEntity(id = "s-company", name = ScopeType.COMPANY, hierarchyLevel = 40)
    private val deptScope = ScopeEntity(id = "s-dept", name = ScopeType.DEPARTMENT, hierarchyLevel = 30)
    private val resourceScope = ScopeEntity(id = "s-resource", name = ScopeType.RESOURCE, hierarchyLevel = 10)

    private val adminRole = RoleEntity(id = "r-admin", name = RoleType.ADMIN, scope = companyScope)
    private val deptHeadRole = RoleEntity(id = "r-head", name = RoleType.DEPARTMENT_HEAD, scope = deptScope)
    private val memberRole = RoleEntity(id = "r-member", name = RoleType.MEMBER, scope = resourceScope)

    private val actionUpdate = ActionEntity(id = "a-update", name = ActionType.UPDATE)
    private val resourceUser = ResourceEntity(id = "res-user", name = ResourceType.USER)

    @BeforeEach
    fun setUp() {
        permissionService = PermissionService(rolePermissionRepository, departmentRepository)
    }

    // --- A. ADMIN (COMPANY Scope) TESTS ---

    @Test
    fun `ADMIN im COMPANY Scope sollte Zugriff haben`() {
        val adminContext = UserContext(
            scope = companyScope,
            scopeInstanceId = null,
            role = adminRole
        )

        // Simuliert eine RolePermissionEntity in DB
        val permission = RolePermissionEntity(
            role = adminRole,
            action = actionUpdate,
            targetScope = companyScope,
            resource = resourceUser
        )

        every {
            rolePermissionRepository.findByActionNameAndResourceName(ActionType.UPDATE, ResourceType.USER)
        } returns listOf(permission)

        val targetUserResource = UserSecurityResource(targetUserId = "fremder-user", departmentId = "fremde-abteilung")

        val hasAccess = permissionService.hasPermission(
            userContexts = listOf(adminContext),
            action = ActionType.UPDATE,
            resource = targetUserResource
        )

        assertTrue(hasAccess)
    }

    // --- B. DEPARTMENT_HEAD (DEPARTMENT Scope) TESTS ---

    @Test
    fun `DEPARTMENT_HEAD sollte Zugriff auf User der eigenen Abteilung haben`() {
        val headContext = UserContext(
            scope = deptScope,
            scopeInstanceId = "dept-100",
            role = deptHeadRole
        )

        val permission = RolePermissionEntity(
            role = deptHeadRole,
            action = actionUpdate,
            targetScope = deptScope,
            resource = resourceUser
        )

        every {
            rolePermissionRepository.findByActionNameAndResourceName(ActionType.UPDATE, ResourceType.USER)
        } returns listOf(permission)

        val userInSameDept = UserSecurityResource(targetUserId = "user-2", departmentId = "dept-100")

        val hasAccess = permissionService.hasPermission(
            userContexts = listOf(headContext),
            action = ActionType.UPDATE,
            resource = userInSameDept
        )

        assertTrue(hasAccess)
    }

    @Test
    fun `DEPARTMENT_HEAD sollte KEINEN Zugriff auf User einer fremden Abteilung haben`() {
        val headContext = UserContext(
            scope = deptScope,
            scopeInstanceId = "dept-100",
            role = deptHeadRole
        )

        val permission = RolePermissionEntity(
            role = deptHeadRole,
            action = actionUpdate,
            targetScope = deptScope,
            resource = resourceUser
        )

        every {
            rolePermissionRepository.findByActionNameAndResourceName(ActionType.UPDATE, ResourceType.USER)
        } returns listOf(permission)

        val userInOtherDept = UserSecurityResource(targetUserId = "user-3", departmentId = "dept-fremd")

        val hasAccess = permissionService.hasPermission(
            userContexts = listOf(headContext),
            action = ActionType.UPDATE,
            resource = userInOtherDept
        )

        assertFalse(hasAccess)
    }

    // --- C. OWNER (RESOURCE Scope) TESTS ---

    @Test
    fun `OWNER im RESOURCE Scope sollte eigenes Objekt bearbeiten duerfen`() {
        val ownerContext = UserContext(
            scope = resourceScope,
            scopeInstanceId = "user-777",
            role = memberRole
        )

        val permission = RolePermissionEntity(
            role = memberRole,
            action = actionUpdate,
            targetScope = resourceScope,
            resource = resourceUser
        )

        every {
            rolePermissionRepository.findByActionNameAndResourceName(ActionType.UPDATE, ResourceType.USER)
        } returns listOf(permission)

        val ownResource = UserSecurityResource(targetUserId = "user-777")
        val fremdeResource = UserSecurityResource(targetUserId = "user-999")

        assertTrue(permissionService.hasPermission(listOf(ownerContext), ActionType.UPDATE, ownResource))
        assertFalse(permissionService.hasPermission(listOf(ownerContext), ActionType.UPDATE, fremdeResource))
    }
}