package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.PlannerSettingsEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlannerSettingsRepository: JpaRepository<PlannerSettingsEntity, String> {
}