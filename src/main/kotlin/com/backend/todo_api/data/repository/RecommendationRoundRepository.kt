package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.RecommendationRoundEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RecommendationRoundRepository: JpaRepository<RecommendationRoundEntity, String> {
}