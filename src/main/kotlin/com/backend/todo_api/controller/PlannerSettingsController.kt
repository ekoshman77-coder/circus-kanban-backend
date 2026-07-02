package com.backend.todo_api.controller

import com.backend.todo_api.data.entity.PlannerSettingsEntity
import com.backend.todo_api.dto.PlannerSettingsDto
import com.backend.todo_api.services.PlannerSettingsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = ["http://localhost:4200"])
class PlannerSettingsController(
    private val settingsService: PlannerSettingsService
) {

    // 📥 Einstellungen laden (Die ID ist die User-GUID!)
    @GetMapping("/{userId}")
    fun getSettings(@PathVariable userId: String): ResponseEntity<PlannerSettingsDto> {

        return ResponseEntity.ok(settingsService.getSettingsForUser(userId))
    }

    // 📤 Einstellungen aktualisieren
    @PutMapping("/{userId}")
    fun updateSettings(
        @PathVariable userId: String,
        @RequestBody updatedDto: PlannerSettingsDto
    ): ResponseEntity<PlannerSettingsDto> {
        updatedDto.userId = userId
        return ResponseEntity.ok(settingsService.update(updatedDto))
    }
}