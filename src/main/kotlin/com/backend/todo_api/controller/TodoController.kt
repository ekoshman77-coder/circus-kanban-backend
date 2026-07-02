package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateTodoDto
import com.backend.todo_api.dto.SyncResultDto
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
        @RequestParam userId: String?
    ): ResponseEntity<List<TodoDto>> { // 🌟 Typisiert auf List<TodoDto>
        return ResponseEntity.ok(todoService.getTodos(userId))
    }

    @PostMapping
    @Operation(summary = "Neues To-Do erstellen", description = "Speichert eine Aufgabe ab und ordnet sie der User-ID zu.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "To-Do erfolgreich erstellt"),
        ApiResponse(responseCode = "400", description = "Fehlende userId im JSON-Body")
    ])
    fun createTodo(@RequestBody dto: CreateTodoDto): ResponseEntity<TodoDto> { // 🌟 Typisiert auf TodoDto
        return ResponseEntity.status(HttpStatus.CREATED).body(todoService.createTodo(dto))
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
        @RequestBody dto: TodoDto
    ): ResponseEntity<TodoUpdateResponse> { // 🌟 Typisiert auf TodoDto
        if (id.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val result = todoService.updateTodo( dto );
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/completed")
    @Operation(summary = "Erledigte Aufgaben eines Users löschen", description = "Löscht alle abgeschlossenen To-Dos NUR für diesen User.")
    fun deleteCompleted(@RequestParam userId: String?): ResponseEntity<Map<String, String>> {
        if (userId.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val count = todoService.deleteCompleted(userId)
        return ResponseEntity.ok(mapOf("message" to "$count erledigte Aufgaben gelöscht."))
    }

    @PostMapping("/delete-bulk")
    @Operation(summary = "Aufgaben löschen", description = "Löscht alle abgeschlossenen To-Dos NUR für diesen User.")
    fun deleteBulk(ids: List<String>): ResponseEntity<Void> {
        todoService.deleteBulk(ids)
        return ResponseEntity.noContent().build()
    }


    @DeleteMapping("/all")
    @Operation(summary = "Alle Aufgaben eines Users löschen", description = "Löscht das komplette Board eines spezifischen Users.")
    fun deleteAll(@RequestParam userId: String?): ResponseEntity<Map<String, String>> {
        println("--------------------------------------------------")
        println("--> JETZT KOMMT WAS AN BEI /all!") // 🌟 Text korrigiert
        println("--> Übergebene userId ist: $userId")
        println("--------------------------------------------------")

        if (userId.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val count = todoService.deleteAll(userId)
        return ResponseEntity.ok(mapOf("message" to "Alle $count Aufgaben gelöscht."))
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Einzelnes To-Do löschen", description = "Löscht eine spezifische Aufgabe anhand ihrer ID.")
    fun deleteTodo(@PathVariable id: String): ResponseEntity<Void> {
        todoService.deleteTodoById(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/bulk")
    @Operation(summary = "Offline-Synchronisation (Bulk-Sync)", description = "Gleicht die Offline-Liste ab und berechnet gesammelte Punkte.")
    fun syncBulk(
        @RequestParam userId: String,
        @RequestBody offlineTodos: List<TodoDto>
    ): ResponseEntity<SyncResultDto> { // 🌟 Typ angepasst!
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
        @RequestParam(required = false) userId: String?,
        @PathVariable milestoneId: String
    ): ResponseEntity<List<TodoDto>> {
        val todos = todoService.getTodosByMilestone(userId, milestoneId)
        return ResponseEntity.ok(todos)
    }
}