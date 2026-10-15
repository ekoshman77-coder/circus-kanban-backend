package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.DepartmentSpecializationEntity
import com.backend.todo_api.model.DepartmentSpecializationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DepartmentSpecializationRepository : JpaRepository<DepartmentSpecializationEntity, String> {
    fun findByName(name: DepartmentSpecializationType): DepartmentSpecializationEntity?
}