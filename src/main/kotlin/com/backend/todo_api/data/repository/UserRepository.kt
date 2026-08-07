package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.TodoEntity
import com.backend.todo_api.data.entity.UserEntity
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<UserEntity, String> {

    fun findByUsernameIgnoreCase(username: String): UserEntity?

    // Nur aktive User (nicht archiviert) für den Warteraum oder das Board holen
    fun findByIsApprovedAndIsArchivedFalse(isApproved: Boolean): List<UserEntity>

    fun findByIsApprovedAndDepartmentIdAndIsArchivedFalse(isApproved: Boolean, departmentId: String?): List<UserEntity>

    //Prüfen, ob ein Benutzername existiert und NICHT archiviert ist (für Login)
    fun findByUsernameIgnoreCaseAndIsArchivedFalse(username: String): UserEntity?

    // 🔍 Holt einfach JEDEN User, der nicht archiviert ist (egal ob approved oder nicht)
    fun findByIsArchivedFalse(): List<UserEntity>
    fun findByIdAndIsArchivedFalse(id: String): UserEntity?
}