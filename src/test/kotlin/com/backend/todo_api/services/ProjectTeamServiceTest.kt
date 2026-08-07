package com.backend.todo_api.services

import com.backend.todo_api.data.entity.ProjectEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.CoffeeAccountRepository
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ProjectMemberRepository
import com.backend.todo_api.data.repository.ProjectRepository
import com.backend.todo_api.data.repository.RoleRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.ProjectNotFoundException
import com.backend.todo_api.exceptions.TeamValidationException
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.model.ActionType
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class ProjectTeamServiceTest {

    private val projectRepository: ProjectRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val coffeeAccountRepository: CoffeeAccountRepository = mockk()
    private val projectMemberRepository: ProjectMemberRepository = mockk()
    private val departmentRepository: DepartmentRepository = mockk()
    private val roleRepository: RoleRepository = mockk()
    private val userContextResolver: UserContextResolver = mockk()
    private val permissionService: PermissionService = mockk()

    private lateinit var projectTeamService: ProjectTeamService

    private val currentUserId = "admin-123"
    private val mockContexts = listOf<UserContext>(mockk())

    @BeforeEach
    fun setUp() {
        projectTeamService = ProjectTeamService(
            projectRepository,
            userRepository,
            coffeeAccountRepository,
            projectMemberRepository,
            departmentRepository,
            roleRepository,
            userContextResolver,
            permissionService
        )

        // Standardmäßig lösen wir den UserContext auf
        every { userContextResolver.resolveContexts(currentUserId) } returns mockContexts
    }

    @Test
    fun `assignUserToProject - sollte Exception werfen wenn Rolle blank ist`() {
        val mockProject = ProjectEntity(id = "proj-1")
        every { projectRepository.findById("proj-1") } returns Optional.of(mockProject)
        every { permissionService.hasPermission(mockContexts, ActionType.UPDATE, any()) } returns true

        val exception = assertThrows<TeamValidationException> {
            projectTeamService.assignUserToProject(currentUserId, "proj-1", "user-1", "   ")
        }
        assertEquals("Es muss zwingend eine Projekt-Rolle übergeben werden!", exception.message)
    }

    @Test
    fun `assignUserToProject - sollte Exception werfen wenn Projekt nicht existiert`() {
        every { projectRepository.findById("invalid-proj") } returns Optional.empty()

        assertThrows<ProjectNotFoundException> {
            projectTeamService.assignUserToProject(currentUserId, "invalid-proj", "user-1", "DEVELOPER")
        }
    }

    @Test
    fun `assignUserToProject - sollte Exception werfen wenn keine Berechtigung vorhanden ist`() {
        val mockProject = ProjectEntity(id = "proj-1")
        every { projectRepository.findById("proj-1") } returns Optional.of(mockProject)
        every { permissionService.hasPermission(mockContexts, ActionType.UPDATE, any()) } returns false

        assertThrows<ActionForbiddenException> {
            projectTeamService.assignUserToProject(currentUserId, "proj-1", "user-1", "DEVELOPER")
        }
    }

    @Test
    fun `assignUserToProject - sollte Exception werfen wenn User nicht existiert`() {
        val mockProject = ProjectEntity(id = "proj-1")
        every { projectRepository.findById("proj-1") } returns Optional.of(mockProject)
        every { permissionService.hasPermission(mockContexts, ActionType.UPDATE, any()) } returns true
        every { projectMemberRepository.findByUserIdAndProjectId("invalid-user", "proj-1") } returns null
        every { userRepository.findById("invalid-user") } returns Optional.empty()

        assertThrows<UserNotFoundException> {
            projectTeamService.assignUserToProject(currentUserId, "proj-1", "invalid-user", "DEVELOPER")
        }
    }

    @Test
    fun `assignUserToProject - Happy Path - sollte User erfolgreich zuweisen`() {
        val mockProject = ProjectEntity(id = "proj-1")
        val mockUser = UserEntity(id = "user-1", firstName = "Max", lastName = "Mustermann", username = "max")

        every { projectRepository.findById("proj-1") } returns Optional.of(mockProject)
        every { permissionService.hasPermission(mockContexts, ActionType.UPDATE, any()) } returns true
        every { projectMemberRepository.findByUserIdAndProjectId("user-1", "proj-1") } returns null
        every { userRepository.findById("user-1") } returns Optional.of(mockUser)
        every { projectMemberRepository.save(any()) } returns mockk()
        every { coffeeAccountRepository.findById("user-1") } returns Optional.empty()

        val result = projectTeamService.assignUserToProject(currentUserId, "proj-1", "user-1", "DEVELOPER")

        assertNotNull(result)
        assertEquals("DEVELOPER", result.projectRole)
        verify(exactly = 1) { projectMemberRepository.save(any()) }
    }
}