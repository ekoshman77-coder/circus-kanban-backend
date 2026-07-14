package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateProjectDto
import com.backend.todo_api.dto.ProjectDashboardStatsDTO
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
        val projects = projectService.getProjectsByWithUser(userId)
        return ResponseEntity.ok(projects)
    }

    // 🔍 Endpunkt für die Einzelabfrage eines Projekts
    @GetMapping("/{id}")
    fun getProjectById(@PathVariable id: String): ResponseEntity<ProjectDto> {
        return ResponseEntity.ok(projectService.getProjectById(id))
    }

    @PostMapping
    fun createProject(@RequestBody dto: CreateProjectDto): ResponseEntity<ProjectDto> {
        val project = projectService.createProject(dto)
        println("project was created")
        return ResponseEntity.status(HttpStatus.CREATED).body(project)
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

    @GetMapping("/statistics/{userId}")
    fun getDashboardStatistics(@PathVariable userId: String): ResponseEntity<ProjectDashboardStatsDTO> {
        val stats = projectService.getDashboardStatistics(userId)
        return ResponseEntity.ok(stats)
    }
}