package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateProjectDto
import com.backend.todo_api.dto.ProjectDashboardStatsDTO
import com.backend.todo_api.dto.ProjectDto
import com.backend.todo_api.services.ProjectService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = ["http://localhost:4200"])
class ProjectController(private val projectService: ProjectService) {

    @GetMapping
    fun getProjects(
        principal: Principal
    ): ResponseEntity<List<ProjectDto>> {
        val userId = getUserIdFromPrincipal(principal)
        // Reicht die userId an den überarbeiteten Service weiter
        val projects = projectService.getProjectsByWithUser(userId)
        return ResponseEntity.ok(projects)
    }

    // Endpunkt für die Einzelabfrage eines Projekts
    @GetMapping("/{id}")
    fun getProjectById(
        @PathVariable id: String,
        principal: Principal
    ): ResponseEntity<ProjectDto> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(projectService.getProjectById(userId, id))
    }

    @PostMapping
    fun createProject(
        @RequestBody dto: CreateProjectDto,
        principal: Principal
    ): ResponseEntity<ProjectDto> {
        val userId = getUserIdFromPrincipal(principal)
        val project = projectService.createProject(userId, dto)
        println("project was created")
        return ResponseEntity.status(HttpStatus.CREATED).body(project)
    }

    @PutMapping("/{id}")
    fun updateProject(
        @PathVariable id: String,
        @RequestBody dto: ProjectDto,
        principal: Principal
    ): ResponseEntity<ProjectDto> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(projectService.updateProject(userId, id, dto))
    }

    @DeleteMapping("/{id}")
    fun deleteProject(
        @PathVariable id: String,
        principal: Principal
        ): ResponseEntity<Void> {
        val userId = getUserIdFromPrincipal(principal)
        projectService.deleteProject(userId, id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/statistics/{userId}")
    fun getDashboardStatistics(@PathVariable userId: String): ResponseEntity<ProjectDashboardStatsDTO> {
        val stats = projectService.getDashboardStatistics(userId)
        return ResponseEntity.ok(stats)
    }
}