package com.backend.todo_api.controller

import com.backend.todo_api.dto.DepartmentDto
import com.backend.todo_api.services.DepartmentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/departments")
@CrossOrigin(origins = ["http://localhost:4200"])
@Tag(name = "Department-Controller", description = "Endpunkte zur Verwaltung von Abteilungen")
class DepartmentController(private val departmentService: DepartmentService) {

    @GetMapping
    @Operation(summary = "Alle Abteilungen abrufen")
    fun getAllDepartments(): ResponseEntity<List<DepartmentDto>> {
        return ResponseEntity.ok(departmentService.getAllDepartments())
    }

    @PostMapping
    @Operation(summary = "Neue Abteilung erstellen")
    fun createDepartment(@RequestBody dto: DepartmentDto): ResponseEntity<DepartmentDto> {
        val created = departmentService.createDepartment(dto.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @PutMapping("/{id}")
    @Operation(summary = "Abteilung umbenennen")
    fun updateDepartment(
        @PathVariable id: String,
        @RequestBody dto: DepartmentDto
    ): ResponseEntity<DepartmentDto> {
        val updated = departmentService.updateDepartment(id, dto.name)
        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Abteilung löschen")
    fun deleteDepartment(@PathVariable id: String): ResponseEntity<Void> {
        departmentService.deleteDepartment(id)
        return ResponseEntity.noContent().build()
    }
}