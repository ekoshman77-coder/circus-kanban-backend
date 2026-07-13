package com.backend.todo_api.controller

import com.backend.todo_api.dto.BulkPomodoroRequest
import com.backend.todo_api.dto.GamificationResult
import com.backend.todo_api.dto.SinglePomodoroRequest
import com.backend.todo_api.services.GamificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/gamification")
// Erlaubt deinem Angular-Frontend den Zugriff (Pfade ggf. anpassen)
@CrossOrigin(origins = ["http://localhost:4200"])
class GamificationController(
    private val gamificationService: GamificationService
) {

    /**
     * Endpunkt für eine einzelne, live beendete Fokus-Sitzung (Online-Modus)
     */
    @PostMapping("/session")
    fun recordSingleSession(@RequestBody request: SinglePomodoroRequest): ResponseEntity<GamificationResult> {
        // Wir übergeben die Daten an den Service, der die XP berechnet,
        // das User-Konto updatet und prüft, ob es ein Level-Up (inklusive neuem KI-Titel) gab!
        val result = gamificationService.processCompletedPomodoro(
            userId = request.userId,
            todoId = request.todoId,
            count = request.count
        )
        return ResponseEntity.ok(result)
    }

    /**
     * Endpunkt für das Synchronisieren aller offline gesammelten Pomodoros (Bulk-Sync)
     */
    @PostMapping("/bulk")
    fun recordBulkSessions(@RequestBody request: BulkPomodoroRequest): ResponseEntity<GamificationResult> {
        // Der Service verarbeitet die gesamte Liste auf einmal, rechnet die XP zusammen
        // und gibt das finale Gamification-Endergebnis an das Frontend zurück.
        val result = gamificationService.processBulkPomodoros(
            userId = request.userId,
            sessions = request.sessions
        )
        return ResponseEntity.ok(result)
    }

    /**
     * Hilfs-Endpunkt, um den aktuellen Stand (z.B. für den Header) abzufragen
     */
    @GetMapping("/state/{userId}")
    fun getGamificationState(@PathVariable userId: String): ResponseEntity<GamificationResult> {
        val result = gamificationService.getGamificationState(userId)
        return ResponseEntity.ok(result)
    }
}