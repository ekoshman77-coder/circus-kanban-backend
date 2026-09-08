package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.NoteEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface NoteRepository: JpaRepository<NoteEntity, String> {

    // FRONTEND: Nur aktive Notizen
    fun findByUserIdAndIsArchivedFalse(userId: String): List<NoteEntity>

    // KI & INTERN: Alle Notizen (auch die archivierten!)
    fun findByUserId(userId: String): List<NoteEntity>

    fun findByIsArchivedFalse(): List<NoteEntity>

    @Query("""
        SELECT n FROM NoteEntity n 
        WHERE n.isArchived = false 
        AND (n.departmentId IS NULL OR n.departmentId = :departmentId)
    """)
    fun findActiveGlobalAndDepartmentNotes(@Param("departmentId") departmentId: String): List<NoteEntity>
    fun findByDepartmentIdAndIsArchivedFalse(deptId: String): List<NoteEntity>

    @Query("""
    SELECT n FROM NoteEntity n 
    JOIN ProjectEntity p ON p.ideaId = n.id 
    WHERE p.id IN :projectIds AND n.isArchived = false
""")
    fun findNotesByProjectIds(@Param("projectIds") projectIds: List<String>): List<NoteEntity>

    @Query("""
    SELECT n FROM NoteEntity n 
    JOIN ProjectEntity p ON p.ideaId = n.id 
    WHERE p.id = :projectId AND n.isArchived = false
""")
    fun findNotesByProjectId(@Param("projectId") projectId: String): List<NoteEntity>
    fun findByIdAndIsArchivedFalse(noteId: String): NoteEntity?
}