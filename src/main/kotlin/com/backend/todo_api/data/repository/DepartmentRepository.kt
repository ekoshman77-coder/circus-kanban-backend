package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.DepartmentEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DepartmentRepository: JpaRepository<DepartmentEntity, String> {
    fun findByNameIgnoreCase(name: String): DepartmentEntity?
}