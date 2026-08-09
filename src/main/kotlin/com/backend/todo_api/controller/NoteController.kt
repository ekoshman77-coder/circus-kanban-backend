package com.backend.todo_api.controller

import com.backend.todo_api.dto.CreateNoteDto
import com.backend.todo_api.dto.NoteDto
import com.backend.todo_api.services.NoteService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = ["http://localhost:4200"])
class NoteController(private val noteService: NoteService   ) {

    @GetMapping
    fun getNotes(
        @RequestParam(required = false) userId: String?,
        principal: Principal?
        ): ResponseEntity<List<NoteDto>> {
        val userId = getUserIdFromPrincipal(principal)
        val notes = noteService.getNotesByUserId(userId)
        return ResponseEntity.ok(notes)
    }

    @GetMapping("/{id}")
    fun getNoteById(
        @PathVariable id: String,
        principal: Principal?
                    ): ResponseEntity<NoteDto> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(noteService.getNoteById(userId, id))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNote(@RequestBody dto: CreateNoteDto,
                   principal: Principal?
    ): ResponseEntity<NoteDto> {
        val currentUserId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(noteService.createNote(currentUserId,dto))
    }

    @PutMapping("/{id}")
    fun updateNote(
        @PathVariable id: String,
        @RequestBody dto: NoteDto,
        principal: Principal?
    ): ResponseEntity<NoteDto> {
        val userId = getUserIdFromPrincipal(principal)
        return ResponseEntity.ok(noteService.updateNote(userId, id, dto))
    }

    @DeleteMapping("/{id}")
    fun deleteNote(
        @PathVariable id: String,
        principal: Principal?
    ): ResponseEntity<Unit> {
        val userId = getUserIdFromPrincipal(principal)
        noteService.deleteNote(userId, id) // 🔑 Und reichen sie sauber weiter
        return ResponseEntity.noContent().build()
    }
}