package com.backend.todo_api.services

import com.backend.todo_api.data.entity.CoffeeAccountEntity
import com.backend.todo_api.data.entity.RoleEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.*
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.dto.UserApproveDto
import com.backend.todo_api.dto.UserDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.RoleType
import com.backend.todo_api.model.ScopeType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional

class UserServiceTest {

    private val userRepository: UserRepository = mockk(relaxed = true)
    private val plannerSettingsRepository: PlannerSettingsRepository = mockk(relaxed = true)
    private val todoRepository: TodoRepository = mockk(relaxed = true)
    private val milestoneRepository: MilestoneRepository = mockk(relaxed = true)
    private val projectRepository: ProjectRepository = mockk(relaxed = true)
    private val coffeeAccountRepository: CoffeeAccountRepository = mockk(relaxed = true)
    private val passwordEncoder: BCryptPasswordEncoder = mockk(relaxed = true)
    private val departmentRepository: DepartmentRepository = mockk(relaxed = true)
    private val roleRepository: RoleRepository = mockk(relaxed = true)
    private val userContextResolver: UserContextResolver = mockk()
    private val permissionService: PermissionService = mockk()
    private val departmentService: DepartmentService = mockk()

    private lateinit var userService: UserService

    private val currentUserId = "user-123"
    private val targetUserId = "user-456"
    private val mockContexts = listOf<UserContext>(mockk())

    @BeforeEach
    fun setUp() {
        userService = UserService(
            userRepository,
            plannerSettingsRepository,
            todoRepository,
            milestoneRepository,
            projectRepository,
            coffeeAccountRepository,
            passwordEncoder,
            departmentRepository,
            roleRepository,
            permissionService,
            userContextResolver,
            departmentService
        )

        // Standard-Mocking für Contexts
        every { userContextResolver.resolveContexts(currentUserId) } returns mockContexts
        every { userRepository.save(any()) } answers { firstArg() }
        every { departmentService.getDepartmentDtoById(any()) } returns DepartmentDto(
            id = "dept-1",
            name = "Development"
        )
        every { coffeeAccountRepository.findById(any()) } returns Optional.empty()
    }

    // --- 1. updateUser TESTS ---

    @Test
    fun `updateUser sollte User erfolgreich aktualisieren, wenn Berechtigung vorliegt`() {
        val targetUser = UserEntity(id = targetUserId, firstName = "Old", lastName = "Name", departmentId = "dept-1")
        val dto = UserDto(firstName = "New", lastName = "Name", isApproved = false)

        every { userRepository.findByIdAndIsArchivedFalse(targetUserId) } returns targetUser
        every { permissionService.hasPermission(mockContexts, ActionType.UPDATE, any()) } returns true
        every { userRepository.save(any()) } answers { firstArg() }

        val result = userService.updateUser(currentUserId, targetUserId, dto)

        assertEquals("New", result.firstName)
        verify(exactly = 1) { userRepository.save(targetUser) }
    }

    @Test
    fun `updateUser sollte ActionForbiddenException werfen, wenn Berechtigung fehlt`() {
        val targetUser = UserEntity(id = targetUserId, firstName = "Old", lastName = "Name", departmentId = "dept-1")
        val dto = UserDto(firstName = "New", lastName = "Name", isApproved = false)

        every { userRepository.findByIdAndIsArchivedFalse(targetUserId) } returns targetUser
        every { permissionService.hasPermission(mockContexts, ActionType.UPDATE, any()) } returns false

        assertThrows(ActionForbiddenException::class.java) {
            userService.updateUser(currentUserId, targetUserId, dto)
        }
    }

    @Test
    fun `updateUser sollte UserNotFoundException werfen, wenn Ziel-User nicht existiert oder archiviert ist`() {
        every { userRepository.findByIdAndIsArchivedFalse(targetUserId) } returns null

        assertThrows(UserNotFoundException::class.java) {
            userService.updateUser(currentUserId, targetUserId, UserDto())
        }
    }

    // --- 2. deleteUser TESTS ---

    @Test
    fun `deleteUser sollte User soft-deleten und Verknuepfungen aufraeumen, wenn Berechtigung vorliegt`() {
        val targetUser = UserEntity(id = targetUserId, departmentId = "dept-1")

        every { userRepository.findByIdAndIsArchivedFalse(targetUserId) } returns targetUser
        every { permissionService.hasPermission(mockContexts, ActionType.DELETE, any()) } returns true
        every { todoRepository.findByAssignedUserId(targetUserId) } returns emptyList()
        every { todoRepository.findByUserId(targetUserId) } returns emptyList()
        every { milestoneRepository.findByAssignedUserId(targetUserId) } returns emptyList()
        every { projectRepository.findByUserId(targetUserId) } returns emptyList()

        userService.deleteUser(currentUserId, targetUserId)

        assertTrue(targetUser.isArchived)
        assertFalse(targetUser.isApproved)
        verify { userRepository.save(targetUser) }
        verify { plannerSettingsRepository.deleteById(targetUserId) }
    }

    @Test
    fun `deleteUser sollte ActionForbiddenException werfen, wenn keine DELETE-Berechtigung vorliegt`() {
        val targetUser = UserEntity(id = targetUserId, departmentId = "dept-1")

        every { userRepository.findByIdAndIsArchivedFalse(targetUserId) } returns targetUser
        every { permissionService.hasPermission(mockContexts, ActionType.DELETE, any()) } returns false

        assertThrows(ActionForbiddenException::class.java) {
            userService.deleteUser(currentUserId, targetUserId)
        }
    }

    // --- 3. approveUser TESTS ---

    @Test
    fun `approveUser sollte User freischalten, wenn Admin-Berechtigung vorliegt`() {
        val targetUser = UserEntity(id = targetUserId, isApproved = false)
        val dto = UserApproveDto(departmentId = "dept-100", departmentRole = RoleType.DEVELOPER)

        every { userRepository.findById(targetUserId) } returns Optional.of(targetUser)
        every { permissionService.hasPermission(mockContexts, ActionType.UPDATE, any()) } returns true
        every { userRepository.save(any()) } answers { firstArg() }

        val result = userService.approveUser(currentUserId, targetUserId, dto)

        assertTrue(result.isApproved)
        assertEquals("dept-100", targetUser.departmentId)
    }

    // --- 4. getUserById TESTS ---

    @Test
    fun `getUserById sollte UserDto zurückgeben, wenn READ-Berechtigung vorliegt`() {
        val targetUser = UserEntity(id = targetUserId, firstName = "Anna")

        every { userRepository.findByIdAndIsArchivedFalse(targetUserId) } returns targetUser
        every { permissionService.hasPermission(mockContexts, ActionType.READ, any()) } returns true

        val result = userService.getUserById(currentUserId, targetUserId)

        assertEquals("Anna", result.firstName)
    }

    @Test
    fun `getApprovedUsers - sammelt User aus DEPARTMENT und PROJECT Contexten`() {
        // GIVEN
        val currentUserId = "user-123"
        val requestingUser = UserEntity(id = currentUserId, departmentId = "dept-1")

        val roleAdmin = RoleEntity(name = RoleType.ADMIN_HEAD)
        val scopeDept = ScopeEntity(name = ScopeType.DEPARTMENT)
        val scopeProj = ScopeEntity(name = ScopeType.PROJECT)

        val deptContext = UserContext(scope = scopeDept, scopeInstanceId = "dept-1", role = roleAdmin)
        val projContext = UserContext(scope = scopeProj, scopeInstanceId = "proj-1", role = roleAdmin)

        every { userRepository.findById(currentUserId) } returns Optional.of(requestingUser)
        every { userContextResolver.resolveContexts(currentUserId) } returns listOf(deptContext, projContext)
        every { permissionService.hasPermission(any(), ActionType.READ, any()) } returns true

        val deptUser = UserEntity(id = "user-dept", firstName = "Dept", lastName = "User", departmentId = "dept-1", isApproved = true)
        val projUser = UserEntity(id = "user-proj", firstName = "Proj", lastName = "User", departmentId = "dept-2", isApproved = true)

        // 🎯 FIX: UserService ruft direkt findByIsApprovedAndIsArchivedFalse(true) auf!
        every { userRepository.findByIsApprovedAndIsArchivedFalse(true) } returns listOf(deptUser, projUser)
        every { coffeeAccountRepository.findById(any()) } returns Optional.of(CoffeeAccountEntity(userId = "dummy"))
        every { departmentService.getDepartmentDtoById(any()) } returns null

        // WHEN
        val result = userService.getApprovedUsers(currentUserId)

        // THEN
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "user-dept" })
        assertTrue(result.any { it.id == "user-proj" })

        verify(exactly = 1) { userRepository.findByIsApprovedAndIsArchivedFalse(true) }
    }

    @Test
    fun `getUnapprovedUsers - wirft ActionForbiddenException wenn Berechtigung fehlt`() {
        // GIVEN
        val currentUserId = "user-no-rights"

        every { userContextResolver.resolveContexts(currentUserId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.READ, any()) } returns false

        // WHEN & THEN
        assertThrows<ActionForbiddenException> {
            userService.getUnapprovedUsers(currentUserId)
        }

        verify(exactly = 0) { userRepository.findByIsApprovedAndIsArchivedFalse(false) }
    }

}