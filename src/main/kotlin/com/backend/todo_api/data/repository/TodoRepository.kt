package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.TodoEntity
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface TodoRepository : JpaRepository<TodoEntity, String> {
    // Das "String" am Ende sagt Spring Boot, dass der Primärschlüssel (die ID) ein Text ist.
    @Transactional
    fun deleteByDone(done: Boolean)
    @Transactional
    fun deleteByDoneTrueAndUserId(userId: String): Int
    @Transactional
    fun deleteByUserId(userId: String): Int
    @Transactional
    fun findByUserIdAndMilestoneIdIn(userId: String, milestoneIds: List<String>): List<TodoEntity>

    fun findByAssignedUserId(assignedUserId: String): List<TodoEntity>
    @Transactional
    @Modifying
    @Query("""
    DELETE FROM TodoEntity 
    WHERE effort = :effort 
      AND done = :done 
      AND createdAt < :todayMidnight
""")
    fun deleteOldPauseTickets(
        @Param("effort") effort: Int,
        @Param("done") done: Boolean, // 🌟 Hier auch auf done angepasst
        @Param("todayMidnight") todayMidnight: Long
    ): Int

    fun findByDoneFalse(): List<TodoEntity>
    fun findByUserIdAndDoneFalse(userId: String): List<TodoEntity>
    // --- 🌍 FRONTEND-SICHT (Nur aktive Todos) ---
    fun findByUserIdAndIsArchivedFalse(userId: String): List<TodoEntity>
    fun findByIdAndIsArchivedFalse(id: String): TodoEntity?
    fun findByUserIdAndMilestoneIdAndIsArchivedFalse(userId: String, milestoneId: String): List<TodoEntity>
    fun findByMilestoneIdAndIsArchivedFalse(milestoneId: String): List<TodoEntity>

    // --- 🧠 KI-SICHT / INTERN (Alle Todos) ---
    // Diese Methoden bleiben für den TrainManager bestehen
    fun findByUserId(userId: String): List<TodoEntity>
    fun findByMilestoneId(milestoneId: String): List<TodoEntity>
    fun findByUserIdAndMilestoneId(userId: String, milestoneId: String): List<TodoEntity>

    @Transactional
    @Modifying
    @Query("UPDATE TodoEntity t SET t.isArchived = true WHERE t.userId = :userId")
    fun archiveAllByUserId(@Param("userId") userId: String): Int

    @Transactional
    @Modifying
    @Query("UPDATE TodoEntity t SET t.isArchived = true WHERE t.done = true AND t.userId = :userId")
    fun archiveCompletedByUserId(@Param("userId") userId: String): Int
    fun findByIsArchivedFalse(): List<TodoEntity>

    //         -- BLOCK 1: Meine privaten, persönlichen Aufgaben
    //        -- BLOCK 2: ALLE Aufgaben aus Projekten, bei denen ich im Team bin
    //     -- Zeitfenster-Schutz für bereits erledigte Aufgaben

    @Query("""
    SELECT t FROM TodoEntity t 
    WHERE t.isArchived = false 
      AND (
        (t.userId = :userId AND (t.milestoneId IS NULL OR t.milestoneId = ''))
        
        OR
        
        (t.milestoneId IS NOT NULL AND t.milestoneId <> '' AND EXISTS (
            SELECT pm FROM ProjectMemberEntity pm 
            JOIN pm.project p
            JOIN p.milestones m
            WHERE pm.user.id = :userId 
              AND m.id = t.milestoneId
        ))
      )
       AND (t.done = false OR t.completedAt IS NULL OR t.completedAt > :cutoffDate)
""")
    fun findRelevantTodosForUser(
        @Param("userId") userId: String,
        @Param("cutoffDate") cutoffDate: Long
    ): List<TodoEntity>

    // 1. Einzelnes To-Do per Mülleimer-Klick archivieren
    @Transactional
    @Modifying
    @Query("UPDATE TodoEntity t SET t.isArchived = true WHERE t.id = :id")
    fun archiveById(@Param("id") id: String): Int

    // 2. Erledigte private Aufgaben im Footer gesammelt archivieren
    @Transactional
    @Modifying
    @Query("""
        UPDATE TodoEntity t 
        SET t.isArchived = true 
        WHERE t.userId = :userId 
          AND t.done = true 
          AND (t.milestoneId IS NULL OR t.milestoneId = '')
    """)
    fun archiveCompletedPrivateTodos(@Param("userId") userId: String): Int

    // 3. ALLE privaten Aufgaben im Footer gesammelt archivieren
    @Transactional
    @Modifying
    @Query("""
        UPDATE TodoEntity t 
        SET t.isArchived = true 
        WHERE t.userId = :userId 
          AND (t.milestoneId IS NULL OR t.milestoneId = '')
    """)
    fun archiveAllPrivateTodos(@Param("userId") userId: String): Int

    @Query("""
    SELECT t FROM TodoEntity t 
    WHERE t.isArchived = false 
      AND t.done = false
      AND (
         (t.userId = :userId AND (t.milestoneId IS NULL OR t.milestoneId = ''))       
        OR
        (t.milestoneId IS NOT NULL AND t.milestoneId <> '' AND t.assignedUserId = :userId)
      )
""")
    fun findActivePlannerTodosForUser(@Param("userId") userId: String): List<TodoEntity>

    @Query("""
    SELECT t FROM TodoEntity t 
    WHERE t.isArchived = false 
      AND t.done = false
      AND t.assignedUserId = :userId
      AND t.milestoneId IS NOT NULL 
      AND t.milestoneId <> ''
""")
    fun findActiveTeamTodosForUser(@Param("userId") userId: String): List<TodoEntity>
}