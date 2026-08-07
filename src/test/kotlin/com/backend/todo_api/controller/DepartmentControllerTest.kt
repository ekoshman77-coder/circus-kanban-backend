package com.backend.todo_api.controller

import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.model.ScopeType
import com.backend.todo_api.services.DepartmentService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.security.Principal

class DepartmentControllerTest {

    private val departmentService: DepartmentService = mockk()
    private val principal: Principal = mockk()

    private lateinit var departmentController: DepartmentController

    private val mockUserId = "user-admin-123"
    private val deptId = "dept-99"

    @BeforeEach
    fun setUp() {
        departmentController = DepartmentController(departmentService)
        every { principal.name } returns mockUserId
    }

    // --- 1. GET /api/departments ---

    @Test
    fun `getAllDepartments sollte Liste und HTTP 200 zurueckgeben`() {
        val departments = listOf(
            DepartmentDto(id = "1", name = "HR", scope = ScopeType.DEPARTMENT),
            DepartmentDto(id = "2", name = "IT", scope = ScopeType.DEPARTMENT)
        )

        every { departmentService.getAllDepartments(mockUserId) } returns departments

        val response = departmentController.getAllDepartments(principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2, response.body?.size)
        assertEquals("HR", response.body?.get(0)?.name)
        verify(exactly = 1) { departmentService.getAllDepartments(mockUserId) }
    }

    // --- 2. POST /api/departments ---

    @Test
    fun `createDepartment sollte HTTP 201 Created und neue Abteilung zurueckgeben`() {
        val inputDto = DepartmentDto(name = "Marketing", scope = ScopeType.DEPARTMENT)
        val createdDto = DepartmentDto(id = deptId, name = "Marketing", scope = ScopeType.DEPARTMENT)

        every { departmentService.createDepartment(mockUserId, inputDto) } returns createdDto

        val response = departmentController.createDepartment(inputDto, principal)

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertEquals(deptId, response.body?.id)
        assertEquals("Marketing", response.body?.name)
        verify(exactly = 1) { departmentService.createDepartment(mockUserId, inputDto) }
    }

    @Test
    fun `createDepartment sollte ActionForbiddenException durchreichen`() {
        val inputDto = DepartmentDto(name = "Marketing")

        every { departmentService.createDepartment(mockUserId, inputDto) } throws ActionForbiddenException("Keine Rechte")

        assertThrows<ActionForbiddenException> {
            departmentController.createDepartment(inputDto, principal)
        }
    }

    // --- 3. PUT /api/departments/{id} ---

    @Test
    fun `updateDepartment sollte HTTP 200 OK und aktualisierte Abteilung zurueckgeben`() {
        val updateDto = DepartmentDto(name = "New HR Name")
        val resultDto = DepartmentDto(id = deptId, name = "New HR Name")

        every { departmentService.updateDepartment(mockUserId, deptId, "New HR Name") } returns resultDto

        val response = departmentController.updateDepartment(deptId, updateDto, principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("New HR Name", response.body?.name)
        verify(exactly = 1) { departmentService.updateDepartment(mockUserId, deptId, "New HR Name") }
    }

    // --- 4. DELETE /api/departments/{id} ---

    @Test
    fun `deleteDepartment sollte HTTP 204 No Content zurueckgeben`() {
        every { departmentService.deleteDepartment(mockUserId, deptId) } just runs

        val response = departmentController.deleteDepartment(deptId, principal)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNull(response.body)
        verify(exactly = 1) { departmentService.deleteDepartment(mockUserId, deptId) }
    }
}