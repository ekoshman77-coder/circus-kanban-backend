package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateProjectDto
import com.backend.todo_api.dto.ProjectDto
import com.backend.todo_api.services.ProjectService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = ["http://localhost:4200"])
class ProjectController(private val projectService: ProjectService) {

    @GetMapping
    fun getProjects(@RequestParam(required = false) userId: String?): ResponseEntity<List<ProjectDto>> {
        return ResponseEntity.ok(projectService.getProjectsByWithUser(userId))
    }

    // 🔍 Endpunkt für die Einzelabfrage eines Projekts
    @GetMapping("/{id}")
    fun getProjectById(@PathVariable id: String): ResponseEntity<ProjectDto> {
        return ResponseEntity.ok(projectService.getProjectById(id))
    }

    @PostMapping
    fun createProject(@RequestBody dto: CreateProjectDto): ResponseEntity<ProjectDto> {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(dto))
    }

    @PutMapping("/{id}")
    fun updateProject(@PathVariable id: String, @RequestBody dto: CreateProjectDto): ResponseEntity<ProjectDto> {
        return ResponseEntity.ok(projectService.updateProject(id, dto))
    }

    @DeleteMapping("/{id}")
    fun deleteProject(@PathVariable id: String): ResponseEntity<Void> {
        projectService.deleteProject(id)
        return ResponseEntity.noContent().build()
    }
}