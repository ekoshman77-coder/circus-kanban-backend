package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.DepartmentSecurityResource
import com.backend.todo_api.model.ResourceType
import com.backend.todo_api.model.ScopeType
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

class DepartmentServiceTest {

    private val departmentRepository: DepartmentRepository = mockk()
    private val userRepository: UserRepository = mockk()
    private val scopeRepository: ScopeRepository = mockk()
    private val userContextResolver: UserContextResolver = mockk()
    private val permissionService: PermissionService = mockk()

    private lateinit var departmentService: DepartmentService

    private val userId = "user-admin-123"
    private val deptId = "dept-1"

    @BeforeEach
    fun setUp() {
        departmentService = DepartmentService(
            departmentRepository,
            userRepository,
            scopeRepository,
            userContextResolver,
            permissionService
        )
    }

    // --- 1. GET ALL DEPARTMENTS ---

    @Test
    fun `getAllDepartments sollte alle Abteilungen zurueckgeben wenn MaxAllowedUserContext existiert`() {
        val dept1 = DepartmentEntity(id = "1", name = "HR")
        val dept2 = DepartmentEntity(id = "2", name = "IT")

        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        // NEU: Mocken des globalen Berechtigungs-Checks
        every {
            permissionService.getMaxAllowedUserContext(any(), ActionType.READ, ResourceType.DEPARTMENT)
        } returns mockk() // Irgendein Kontext wird zurückgegeben

        every { departmentRepository.findAll() } returns listOf(dept1, dept2)

        val result = departmentService.getAllDepartments(userId)

        assertEquals(2, result.size)
        assertEquals("HR", result[0].name)
        assertEquals("IT", result[1].name)
    }

    @Test
    fun `getAllDepartments sollte leere Liste zurueckgeben wenn kein READ-Recht vorhanden ist`() {
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        // NEU: Mocken, dass kein Kontext gefunden wird
        every {
            permissionService.getMaxAllowedUserContext(any(), ActionType.READ, ResourceType.DEPARTMENT)
        } returns null

        val result = departmentService.getAllDepartments(userId)

        assertTrue(result.isEmpty())
    }

    // --- 2. CREATE DEPARTMENT ---

    @Test
    fun `createDepartment sollte Abteilung speichern wenn Rechte vorhanden sind`() {
        val dto = DepartmentDto(id = deptId, name = "Marketing", scope = ScopeType.DEPARTMENT)

        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns true
        every { departmentRepository.findByNameIgnoreCase("Marketing") } returns null

        // Scope Entity Mocking
        every { scopeRepository.findByName(ScopeType.DEPARTMENT) } returns ScopeEntity(name = ScopeType.DEPARTMENT, hierarchyLevel = 20)
        every { departmentRepository.save(any()) } answers { firstArg() }

        val result = departmentService.createDepartment(userId, dto)

        assertEquals("Marketing", result.name)
        verify { departmentRepository.save(any()) }
    }

    @Test
    fun `createDepartment sollte ActionForbiddenException werfen wenn Rechte fehlen`() {
        val dto = DepartmentDto(name = "Marketing")

        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns false

        assertThrows<ActionForbiddenException> {
            departmentService.createDepartment(userId, dto)
        }
    }

    // --- 3. UPDATE DEPARTMENT ---

    @Test
    fun `updateDepartment sollte Namen aendern wenn Rechte vorhanden sind`() {
        val existingDept = DepartmentEntity(id = deptId, name = "Old Name")

        every { departmentRepository.findById(deptId) } returns Optional.of(existingDept)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true
        every { departmentRepository.findByNameIgnoreCase("New Name") } returns null
        every { departmentRepository.save(any()) } answers { firstArg() }

        val result = departmentService.updateDepartment(userId, deptId, "New Name")

        assertEquals("New Name", result.name)
        verify { departmentRepository.save(existingDept) }
    }

    @Test
    fun `updateDepartment sollte Exception werfen wenn Admin Abteilung geaendert wird`() {
        val adminDept = DepartmentEntity(id = deptId, name = AppConstants.ADMIN_DEPARTMENT_NAME)

        every { departmentRepository.findById(deptId) } returns Optional.of(adminDept)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true

        assertThrows<IllegalArgumentException> {
            departmentService.updateDepartment(userId, deptId, "Hacked Admin")
        }
    }

    // --- 4. DELETE DEPARTMENT ---

    @Test
    fun `deleteDepartment sollte Abteilung loeschen wenn Rechte vorhanden und keine User zugewiesen sind`() {
        val dept = DepartmentEntity(id = deptId, name = "Sales")

        every { departmentRepository.findById(deptId) } returns Optional.of(dept)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.DELETE, any()) } returns true
        every { userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, deptId) } returns emptyList()
        every { departmentRepository.deleteById(deptId) } just Runs

        assertDoesNotThrow {
            departmentService.deleteDepartment(userId, deptId)
        }

        verify { departmentRepository.deleteById(deptId) }
    }

    @Test
    fun `deleteDepartment sollte IllegalStateException werfen wenn noch User zugewiesen sind`() {
        val dept = DepartmentEntity(id = deptId, name = "Sales")
        val activeUser = UserEntity(id = "user-1")

        every { departmentRepository.findById(deptId) } returns Optional.of(dept)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.DELETE, any()) } returns true
        every { userRepository.findByIsApprovedAndDepartmentIdAndIsArchivedFalse(true, deptId) } returns listOf(activeUser)

        assertThrows<IllegalStateException> {
            departmentService.deleteDepartment(userId, deptId)
        }
    }
}