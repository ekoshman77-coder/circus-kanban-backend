package com.backend.todo_api.data.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "recommendation_rounds")
class RecommendationRoundEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    var userId: String = "",

    var createdAt: Long = 0,

    @OneToMany(
        mappedBy = "round",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    var recommendations: MutableList<PlannerRecommendationEntity> = mutableListOf()
)