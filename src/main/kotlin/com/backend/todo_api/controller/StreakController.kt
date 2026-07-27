package com.backend.todo_api.controller

import com.backend.todo_api.dto.StreakInfoDto
import com.backend.todo_api.services.StreakService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/streaks")
class StreakController(private val streakService: StreakService) {

    @GetMapping("/sync/{userId}")
    fun syncStreak(@PathVariable userId: String): ResponseEntity<StreakInfoDto> {
        // Ruft den Service auf, der initialisiert/prüft und das Dto liefert
        val streakInfo = streakService.syncAndGetStreakInfo(userId)
        return ResponseEntity.ok(streakInfo)
    }
}