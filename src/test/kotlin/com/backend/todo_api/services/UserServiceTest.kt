package com.backend.todo_api.services

import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.*
import com.backend.todo_api.dto.UserApproveDto
import com.backend.todo_api.dto.UserDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.model.ActionType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        )

        // Standard-Mocking für Contexts
        every { userContextResolver.resolveContexts(currentUserId) } returns mockContexts
        every { userRepository.save(any()) } answers { firstArg() }
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
        val dto = UserApproveDto(departmentId = "dept-100")

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
}