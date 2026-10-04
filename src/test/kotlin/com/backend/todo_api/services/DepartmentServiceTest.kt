package com.backend.todo_api.services

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.entity.ScopeEntity
import com.backend.todo_api.data.entity.UserEntity
import com.backend.todo_api.data.repository.DepartmentRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.data.repository.UserRepository
import com.backend.todo_api.dto.CreateDepartmentDto
import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.mapper.DepartmentMapper
import com.backend.todo_api.model.ActionType
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.model.UserContext
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
    private val departmentMapper: DepartmentMapper = mockk()

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
            permissionService,
            departmentMapper
        )
    }

    // --- 1. GET ALL DEPARTMENTS ---

    @Test
    fun `getAllDepartments sollte alle Abteilungen zurueckgeben wenn COMPANY Scope und Berechtigung vorliegt`() {
        // GIVEN
        val companyScope = ScopeEntity(name = ScopeType.COMPANY)
        val companyContext = UserContext(scope = companyScope, scopeInstanceId = null, role = mockk())

        val dept1 = DepartmentEntity(id = "1", name = "HR")
        val dept2 = DepartmentEntity(id = "2", name = "IT")

        val dto1 = DepartmentDto(id = "1", name = "HR")
        val dto2 = DepartmentDto(id = "2", name = "IT")

        every { userContextResolver.resolveContexts(userId) } returns listOf(companyContext)
        every { permissionService.hasPermission(listOf(companyContext), ActionType.READ, any()) } returns true
        every { departmentRepository.findAll() } returns listOf(dept1, dept2)

        // Mappings mocken
        every { departmentMapper.toDto(dept1) } returns dto1
        every { departmentMapper.toDto(dept2) } returns dto2

        // WHEN
        val result = departmentService.getAllDepartments(userId)

        // THEN
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "HR" })
        assertTrue(result.any { it.name == "IT" })
        verify(exactly = 1) { departmentRepository.findAll() }
    }

    @Test
    fun `getAllDepartments sollte leere Liste zurueckgeben wenn kein READ-Recht vorhanden ist`() {
        // GIVEN
        val companyScope = ScopeEntity(name = ScopeType.COMPANY)
        val companyContext = UserContext(scope = companyScope, scopeInstanceId = null, role = mockk())

        every { userContextResolver.resolveContexts(userId) } returns listOf(companyContext)
        every { permissionService.hasPermission(listOf(companyContext), ActionType.READ, any()) } returns false

        // WHEN
        val result = departmentService.getAllDepartments(userId)

        // THEN
        assertTrue(result.isEmpty())
        verify(exactly = 0) { departmentRepository.findAll() }
    }

    @Test
    fun `createDepartment sollte Abteilung speichern wenn Rechte vorhanden sind`() {
        // GIVEN
        val dto = CreateDepartmentDto(name = "Marketing")
        val entityToSave = DepartmentEntity(name = "Marketing")
        val savedEntity = DepartmentEntity(id = "dept-marketing-1", name = "Marketing")
        val expectedDto = DepartmentDto(id = "dept-marketing-1", name = "Marketing")

        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns true
        every { departmentRepository.findByNameIgnoreCase("Marketing") } returns null

        // Mappings mocken
        every { departmentMapper.toEntity(dto) } returns entityToSave
        every { departmentRepository.save(entityToSave) } returns savedEntity
        every { departmentMapper.toDto(savedEntity) } returns expectedDto

        // WHEN
        val result = departmentService.createDepartment(userId, dto)

        // THEN
        assertEquals("Marketing", result.name)
        verify { departmentRepository.save(entityToSave) }
    }

    @Test
    fun `createDepartment sollte ActionForbiddenException werfen wenn Rechte fehlen`() {
        val dto = CreateDepartmentDto(name = "Marketing")

        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.CREATE, any()) } returns false

        assertThrows<ActionForbiddenException> {
            departmentService.createDepartment(userId, dto)
        }
    }

    // --- 3. UPDATE DEPARTMENT ---

    @Test
    fun `updateDepartment sollte Namen aendern wenn Rechte vorhanden sind`() {
        // GIVEN
        val existingEntity = DepartmentEntity(id = deptId, name = "Old Name")
        val inputDto = DepartmentDto(id = deptId, name = "New Name")
        val updatedEntity = DepartmentEntity(id = deptId, name = "New Name")
        val expectedDto = DepartmentDto(id = deptId, name = "New Name")

        every { departmentRepository.findById(deptId) } returns Optional.of(existingEntity)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true
        every { departmentMapper.updateEntityFromDto(inputDto, existingEntity) } returns updatedEntity
        every { departmentRepository.findByNameIgnoreCase("New Name") } returns null
        every { departmentRepository.save(updatedEntity) } returns updatedEntity
        every { departmentMapper.toDto(updatedEntity) } returns expectedDto

        // WHEN
        val result = departmentService.updateDepartment(userId, deptId, inputDto)

        // THEN
        assertEquals("New Name", result.name)
        verify { departmentRepository.save(updatedEntity) }
    }

    @Test
    fun `updateDepartment sollte Exception werfen wenn Admin Abteilung geaendert wird`() {
        // GIVEN
        val adminEntity = DepartmentEntity(id = deptId, name = AppConstants.ADMIN_DEPARTMENT_NAME)
        val inputDto = DepartmentDto(id = deptId, name = "Hacked Admin")

        every { departmentRepository.findById(deptId) } returns Optional.of(adminEntity)
        every { userContextResolver.resolveContexts(userId) } returns emptyList()
        every { permissionService.hasPermission(any(), ActionType.UPDATE, any()) } returns true

        // WHEN & THEN
        assertThrows< IllegalArgumentException > {
            departmentService.updateDepartment(userId, deptId, inputDto)
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