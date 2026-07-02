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
    fun findByUserId(userId: String): List<TodoEntity>
    @Transactional
    fun deleteByDoneTrueAndUserId(userId: String): Int
    @Transactional
    fun deleteByUserId(userId: String): Int
    @Transactional
    fun findByUserIdAndMilestoneId(userId: String, milestoneId: String): List<TodoEntity>
    @Transactional
    fun findByMilestoneId(milestoneId: String): List<TodoEntity>
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
}