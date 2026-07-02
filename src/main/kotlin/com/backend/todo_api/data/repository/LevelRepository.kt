package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.LevelEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface LevelRepository : JpaRepository<LevelEntity, Int> {
    fun findByLevel(level: Int): Optional<LevelEntity>
}