package com.backend.todo_api.controller

import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.dto.MilestoneDto
import com.backend.todo_api.dto.toEntity
import com.backend.todo_api.services.MilestoneService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/milestones")
class MilestoneController(
    private val milestoneService: MilestoneService // 🎯 Nur noch den Service injizieren!
) {

    @GetMapping("/{id}")
    fun getMilestoneById(@PathVariable id: String): ResponseEntity<MilestoneDto> {
        val milestone = milestoneService.getMilestoneById(id)
        return ResponseEntity.ok(milestone)
    }

    @PostMapping
    fun createMilestone(@RequestBody dto: MilestoneDto): ResponseEntity<MilestoneDto> {
        val saved = milestoneService.createMilestone(dto)
        return ResponseEntity.ok(saved)
    }

    @PutMapping("/{id}")
    fun updateMilestone(@PathVariable id: String, @RequestBody dto: MilestoneDto): ResponseEntity<MilestoneDto> {
        val updated = milestoneService.updateMilestone(id, dto)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build()
    }

    @DeleteMapping("/{id}")
    fun deleteMilestone(@PathVariable id: String): ResponseEntity<Void> {
        val deleted = milestoneService.deleteMilestone(id)
        return if (deleted) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }
}