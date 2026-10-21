package com.backend.todo_api.controller

import com.backend.todo_api.dto.SanitizationFeedbackDto
import com.backend.todo_api.services.SanitizationService
import com.backend.todo_api.services.TodoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/sanitize")
class SanitizationController(
    val todoService: TodoService
) {

    @PostMapping("/apply")
    fun applySanitization(
        @RequestBody feedbackDto: SanitizationFeedbackDto,
        principal: Principal
    ): ResponseEntity<Void> {
        val userId = getUserIdFromPrincipal(principal)
        todoService.applySanitizationToTodo(userId, feedbackDto)
        return ResponseEntity.ok().build()
    }
}