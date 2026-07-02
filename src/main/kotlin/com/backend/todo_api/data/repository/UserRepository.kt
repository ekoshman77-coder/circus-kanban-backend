package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<UserEntity, String> {

    // 🔍 Spring generiert automatisch: "SELECT * FROM users WHERE LOWER(username) = LOWER(?)"
    fun findByUsernameIgnoreCase(username: String): UserEntity?
}