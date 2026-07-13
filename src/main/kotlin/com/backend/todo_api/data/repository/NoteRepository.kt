package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.NoteEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NoteRepository: JpaRepository<NoteEntity, String> {

    // FRONTEND: Nur aktive Notizen
    fun findByUserIdAndIsArchivedFalse(userId: String): List<NoteEntity>

    // KI & INTERN: Alle Notizen (auch die archivierten!)
    fun findByUserId(userId: String): List<NoteEntity>

    fun findByIsArchivedFalse(): List<NoteEntity>
}