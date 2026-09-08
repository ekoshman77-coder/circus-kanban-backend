package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateTodoDto
import com.backend.todo_api.dto.QuickPanelMode
import com.backend.todo_api.dto.SyncResultDto
import com.backend.todo_api.dto.TodoBulkDto
import com.backend.todo_api.dto.TodoDto
import com.backend.todo_api.dto.TodoUpdateResponse
import com.backend.todo_api.services.TodoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.security.Principal

@RestController
@RequestMapping("/api/todos")
@CrossOrigin(origins = ["http://localhost:4200"])
@Tag(name = "Todo-Controller", description = "Mandantenfähige Verwaltung der To-Do-Aufgaben per User-ID")
class TodoController(private val todoService: TodoService) {

    @GetMapping
    @Operation(summary = "To-Dos eines Users laden", description = "Gibt alle Aufgaben zurück, die der übergebenen User-ID gehören.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Liste der Aufgaben erfolgreich geladen"),
        ApiResponse(responseCode = "400", description = "Fehlende oder leere userId")
    ])
    fun getTodos(
        @Parameter(description = "Die eindeutige ID des eingeloggten Users", required = false)
        principal: Principal?
    ): ResponseEntity<List<TodoDto>> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(todoService.getTodos(userId))
    }

    @PostMapping
    @Operation(summary = "Neues To-Do erstellen", description = "Speichert eine Aufgabe ab und ordnet sie der User-ID zu.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "To-Do erfolgreich erstellt"),
        ApiResponse(responseCode = "400", description = "Fehlende userId im JSON-Body")
    ])
    fun createTodo(
        @RequestBody dto: CreateTodoDto,
        principal: Principal?
        ): ResponseEntity<TodoDto> {
        val currentUserId = getUserIdFromPrincipal(principal)
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.createTodo(currentUserId, dto))
    }

    @PutMapping("/{id}")
    @Operation(summary = "Bestehendes To-Do aktualisieren", description = "Aktualisiert den Status, Text oder die Spalte einer Aufgabe anhand ihrer ID.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "To-Do erfolgreich aktualisiert"),
        ApiResponse(responseCode = "400", description = "Ungültige ID oder fehlerhafte Daten"),
        ApiResponse(responseCode = "404", description = "To-Do mit dieser ID oder User nicht gefunden")
    ])
    fun updateTodo(
        @PathVariable id: String,
        @RequestBody dto: TodoDto,
        principal: Principal?
    ): ResponseEntity<TodoUpdateResponse> { // 🌟 Typisiert auf TodoDto
        if (id.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val userId = getUserIdFromPrincipal(principal)
        val result = todoService.updateTodo(userId, dto );
        return ResponseEntity.ok(result)
    }

    @PostMapping("/completed") // 🌟 Geändert zu PostMapping
    @Operation(summary = "Erledigte private Aufgaben eines Users löschen (wird archiviert)")
    fun deleteCompleted(
        principal: Principal?
        ): ResponseEntity<Map<String, String>> {
        val userId = getUserIdFromPrincipal(principal)
        if (userId.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        // 🌟 Ruft jetzt die neue, sichere Service-Methode auf!
        todoService.deleteCompletedPrivateTodos(userId)
        return ResponseEntity.ok(mapOf("message" to "Erledigte private Aufgaben gelöscht."))
    }

    @PostMapping("/all") // 🌟 Geändert zu PostMapping
    @Operation(summary = "Alle privaten Aufgaben eines Users löschen (wird archiviert)")
    fun deleteAll(
        principal: Principal?
    ): ResponseEntity<Map<String, String>> {
        val userId = getUserIdFromPrincipal(principal)
        if (userId.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        // 🌟 Ruft jetzt die neue, sichere Service-Methode auf!
        todoService.deleteAllPrivateTodos(userId)
        return ResponseEntity.ok(mapOf("message" to "Alle privaten Aufgaben gelöscht."))
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Einzelnes To-Do löschen", description = "Löscht eine spezifische Aufgabe anhand ihrer ID.")
    fun deleteTodo(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Void> {
        val userId = getUserIdFromPrincipal(principal)
        todoService.deleteTodoById(userId, id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/bulk")
    @Operation(summary = "Offline-Synchronisation (Bulk-Sync)", description = "Gleicht die Offline-Liste ab und berechnet gesammelte Punkte.")
    fun syncBulk(
        @RequestBody offlineTodos: List<TodoBulkDto>,
        principal: Principal?
    ): ResponseEntity<SyncResultDto> { // 🌟 Typ angepasst!
        val userId = getUserIdFromPrincipal(principal)
        if (userId.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val syncResult = todoService.syncBulkTodos(userId, offlineTodos)
        return ResponseEntity.ok(syncResult)
    }

    @GetMapping("/milestone/{milestoneId}")
    @Operation(
        summary = "To-Dos nach Meilenstein filtern",
        description = "Gibt alle Aufgaben eines Users zurück, die an einen bestimmten Meilenstein gekoppelt sind."
    )
    fun getTodosByMilestone(
        @PathVariable milestoneId: String,
        principal: Principal?
    ): ResponseEntity<List<TodoDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val todos = todoService.getTodosByMilestone(userId, milestoneId)
        return ResponseEntity.ok(todos)
    }

    /**
     * 📋 Holt alle für den User relevanten Todos (inkl. Projekt- & Privat-Tickets)
     * Schützt das Frontend vor Datenmüll durch das 30-Tage-Zeitfenster.
     */
    @GetMapping("/relevant")
    fun getRelevantTodos(
        @RequestParam(defaultValue = "30") daysLookback: Int,
        principal: Principal?
    ): ResponseEntity<List<TodoDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val todos = todoService.getRelevantTodos(userId, daysLookback)
        return ResponseEntity.ok(todos)
    }

    /**
     * 🧠 Der KI-Endpunkt für dein QuickPanel!
     * Liefert eine saubere Liste der 6 am häufigsten genutzten Core-Tasks.
     */
    @GetMapping("/quick-predictions")
    fun getQuickPanelPredictions(
        @RequestParam modus: QuickPanelMode
    ): ResponseEntity<List<String>> {
        // Ruft deine intelligente Methode auf, die AiTextUtil nutzt!
        val predictions = todoService.getIntelligentQuickTodos(modus)
        return ResponseEntity.ok(predictions)
    }
}