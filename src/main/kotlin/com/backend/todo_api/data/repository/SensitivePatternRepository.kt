package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.SensitivePatternEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SensitivePatternRepository: JpaRepository<SensitivePatternEntity, String> {
    fun findByPatternValueAndPatternType(patternValue: String, patternType: String): List<SensitivePatternEntity>
}