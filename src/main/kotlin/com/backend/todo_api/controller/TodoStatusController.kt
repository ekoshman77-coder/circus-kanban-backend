package com.backend.todo_api.controller

import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.services.GamificationService
import com.backend.todo_api.services.TodoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/gamification")
@CrossOrigin(origins = ["http://localhost:4200"])
class TodoStatusController(
    private val todoService: TodoService,
    private val gamificationService: GamificationService
) {
    @GetMapping("/{userId}")
    fun getGamificationState(@PathVariable userId: String): ResponseEntity<GamificationResult> {
        println("bin im TodoStatusController getState")
        val state = gamificationService.getGamificationState(userId)
        return ResponseEntity.ok(state)
    }
}