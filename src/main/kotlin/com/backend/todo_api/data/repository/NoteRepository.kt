package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.NoteEntity
import org.springframework.data.jpa.repository.JpaRepository

interface NoteRepository: JpaRepository<NoteEntity, String>{
    fun findByUserId(userId: String): List<NoteEntity>
}