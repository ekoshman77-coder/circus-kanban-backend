package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreatePatternFromSelectionDto
import com.backend.todo_api.dto.PredictReasonRequestDto
import com.backend.todo_api.dto.SanitizationFeedbackDto
import com.backend.todo_api.services.sanitization.SanitizationService
import com.backend.todo_api.services.TodoService
import com.backend.todo_api.services.sanitization.SanitizationOrchestrator
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@CrossOrigin(origins = ["http://localhost:4200"])
@RequestMapping("/api/sanitize")
class SanitizationController(
    val todoService: TodoService,
    val sanitizationOrchestrator: SanitizationOrchestrator,
    val sanitizationService: SanitizationService
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

    // 🚀 NEUER ENDPUNKT: Pattern aus Textauswahl erstellen & rückwirkend scannen
    @PostMapping("/pattern-from-selection")
    fun createPatternFromSelection(
        @RequestBody dto: CreatePatternFromSelectionDto,
        principal: Principal
    ): ResponseEntity<Void> {
        // Hier könntest du optional noch einen Admin-Check einbauen,
        // falls dein Security-Kontext das erfordert (z.B. über permissionService)
        val userId = getUserIdFromPrincipal(principal)
        sanitizationOrchestrator.createPatternFromSelectionAndRescan(userId, dto)
        return ResponseEntity.ok().build()
    }

//    /**
//     * 📋 GET /api/sanitize/reasons
//     * Liefert alle bekannten Kategorien für Dropdowns/Datalists im Frontend.
//     */
//    @GetMapping("/reasons")
//    fun getReasons(): List<String> {
//        val reasons = sanitizationService.getUniqueReasons()
//        return reasons
//    }
//
//    /**
//     * 🔮 POST /api/sanitize/predict-reason
//     * Analysiert Text und Typ und schlägt einen intelligenten Grund (Reason) vor.
//     */
//    @PostMapping("/predict-reason")
//    fun predictReason(@RequestBody dto: PredictReasonRequestDto): Map<String, String> {
//        val suggestedReason = sanitizationService.predictReason(dto.selectedText, dto.patternType.name)
//        return mapOf("reason" to suggestedReason)
//    }
}