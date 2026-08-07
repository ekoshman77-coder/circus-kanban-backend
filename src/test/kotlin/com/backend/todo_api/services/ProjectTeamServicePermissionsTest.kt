package com.backend.todo_api.services

import com.backend.todo_api.data.entity.*
import com.backend.todo_api.data.repository.*
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.exceptions.ProjectNotFoundException
import com.backend.todo_api.exceptions.UserNotFoundException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.RoleType
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class ProjectTeamServicePermissionsTest {

    private val projectRepository: ProjectRepository = mockk()
    private val projectMemberRepository: ProjectMemberRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val roleRepository: RoleRepository = mockk()
    private val permissionService: PermissionService = mockk()
    private val userContextResolver: UserContextResolver = mockk()
    private val coffeeAccountRepository: CoffeeAccountRepository = mockk()
    private val departmentRepository: DepartmentRepository = mockk()

    private lateinit var projectTeamService: ProjectTeamService

    private val currentUserId = "user-admin"
    private val targetUserId = "user-member"
    private val projectId = "proj-1"
    private val deptId = "dept-dev"

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
    }

    @Test
    fun `assignUserToProject sollte Mitglied erfolgreich hinzufügen bei ausreichender Berechtigung`() {
        val project = ProjectEntity(id = projectId)
        val targetUser = UserEntity(id = targetUserId, isArchived = false)

        // 1. Context & Permissions
        every { userContextResolver.resolveContexts(currentUserId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true

        // 2. Repositories
        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { projectMemberRepository.findByUserIdAndProjectId(targetUserId, projectId) } returns null
        every { userRepository.findById(targetUserId) } returns Optional.of(targetUser)

        // MockK Fix: Verhindert ClassCastException beim Speichern
        every { projectMemberRepository.save(any()) } answers { firstArg() }

        // Am Ende der Methode wird das Kaffeekonto geladen
        every { coffeeAccountRepository.findById(targetUserId) } returns Optional.empty()

        assertDoesNotThrow {
            projectTeamService.assignUserToProject(
                currentUserId = currentUserId,
                projectId = projectId,
                userId = targetUserId,
                role = RoleType.DEVELOPER.toString()
            )
        }

        verify { projectMemberRepository.save(any()) }
    }

    @Test
    fun `assignUserToProject sollte ActionForbiddenException werfen wenn Rechte fehlen`() {
        val project = ProjectEntity(id = projectId)

        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { userContextResolver.resolveContexts(currentUserId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns false

        assertThrows(ActionForbiddenException::class.java) {
            projectTeamService.assignUserToProject(
                currentUserId = currentUserId,
                projectId = projectId,
                userId = targetUserId,
                role = RoleType.DEVELOPER.toString()
            )
        }
    }

    // --- GET MEMBERS FOR PROJECT TESTS ---

    @Test
    fun `getMembersForProject sollte Liste von Mitgliedern zurückgeben wenn Berechtigung vorliegt`() {
        val project = ProjectEntity(id = projectId)
        val memberUser = UserEntity(id = targetUserId, username = "Developer")
        val roleEntity = RoleEntity(name = RoleType.DEVELOPER)

        val membership = ProjectMemberEntity(project = project, user = memberUser, role = roleEntity)
        project.teamMemberships = mutableListOf(membership)

        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { userContextResolver.resolveContexts(currentUserId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.READ, any()) } returns true
        every { coffeeAccountRepository.findById(targetUserId) } returns Optional.empty()

        val result = projectTeamService.getMembersForProject(projectId, currentUserId)

        assertEquals(1, result.size)
        assertEquals("DEVELOPER", result[0].projectRole)
    }

    // --- REMOVE USER TESTS ---

    @Test
    fun `removeUserFromProject sollte Mitglied aus Projekt entfernen`() {
        val project = ProjectEntity(id = projectId)
        val membership = ProjectMemberEntity(project = project, user = UserEntity(id = targetUserId))

        every { projectRepository.findById(projectId) } returns Optional.of(project)
        every { userContextResolver.resolveContexts(currentUserId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true
        every { projectMemberRepository.findByUserIdAndProjectId(targetUserId, projectId) } returns membership
        every { projectMemberRepository.delete(membership) } just Runs

        assertDoesNotThrow {
            projectTeamService.removeUserFromProject(projectId, targetUserId, currentUserId)
        }

        verify { projectMemberRepository.delete(membership) }
    }

    // --- COFFEE ACCOUNT TESTS ---

    @Test
    fun `updateCoffeeAccount sollte Kaffeekonto aktualisieren`() {
        val targetUser = UserEntity(id = targetUserId, departmentId = deptId, isArchived = false)
        val coffeeAccount = CoffeeAccountEntity(userId = targetUserId, balance = 0.0f)

        every { userContextResolver.resolveContexts(currentUserId) } returns emptyList()
        every { userRepository.findById(targetUserId) } returns Optional.of(targetUser)
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true
        every { coffeeAccountRepository.findById(targetUserId) } returns Optional.of(coffeeAccount)
        every { coffeeAccountRepository.save(any()) } answers { firstArg() }

        val response = projectTeamService.updateCoffeeAccount(
            currentUserId = currentUserId,
            targetUserId = targetUserId,
            balance = 15.5f,
            role = "COFFEE_MASTER",
            emoji = "☕"
        )

        assertEquals(15.5f, coffeeAccount.balance)
        assertEquals("COFFEE_MASTER", coffeeAccount.role)
        verify { coffeeAccountRepository.save(coffeeAccount) }
    }

    @Test
    fun `updateCoffeeAccount sollte UserNotFoundException werfen wenn Target-User archiviert ist`() {
        val targetUser = UserEntity(id = targetUserId, departmentId = deptId, isArchived = true)

        every { userContextResolver.resolveContexts(currentUserId) } returns emptyList()
        every { userRepository.findById(targetUserId) } returns Optional.of(targetUser)
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true

        assertThrows(UserNotFoundException::class.java) {
            projectTeamService.updateCoffeeAccount(
                currentUserId = currentUserId,
                targetUserId = targetUserId,
                balance = 10.0f,
                role = "MEMBER",
                emoji = "☕"
            )
        }
    }
}