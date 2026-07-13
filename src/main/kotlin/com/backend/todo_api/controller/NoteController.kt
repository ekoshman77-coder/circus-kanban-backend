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

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = ["http://localhost:4200"])
class NoteController(private val noteService: NoteService) {

    @GetMapping
    fun getNotes(@RequestParam(required = false) userId: String?): ResponseEntity<List<NoteDto>> {
        return ResponseEntity.ok(noteService.getNotesByUserId(userId))
    }

    @GetMapping("/{id}")
    fun getNoteById(@PathVariable id: String): ResponseEntity<NoteDto> { // 🔑 String id
        return ResponseEntity.ok(noteService.getNoteById(id))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createNote(@RequestBody dto: CreateNoteDto): ResponseEntity<NoteDto> {
        return ResponseEntity.ok(noteService.createNote(dto))
    }

    @PutMapping("/{id}")
    fun updateNote(@PathVariable id: String, @RequestBody dto: NoteDto): ResponseEntity<NoteDto> {
        return ResponseEntity.ok(noteService.updateNote(id, dto))
    }

    @DeleteMapping("/{id}")
    fun deleteNote(
        @PathVariable id: String,
        @RequestParam userId: String // 🎯 Hier fangen wir die userId ab!
    ): ResponseEntity<Unit> {
        noteService.deleteNote(id, userId) // 🔑 Und reichen sie sauber weiter
        return ResponseEntity.noContent().build()
    }
}