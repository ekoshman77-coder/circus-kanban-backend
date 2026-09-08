package com.backend.todo_api.data.entity

import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.PlannerType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "planner-recommendation")
class PlannerRecommendationEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    var round: RecommendationRoundEntity = RecommendationRoundEntity(),

    @Enumerated(EnumType.STRING)
    var plannerType: PlannerType = PlannerType.BAYES,

    var todoId: String = "",

    var score: Double = 0.0,

    @Enumerated(EnumType.STRING)
    var energyLevel: EnergyLevel = EnergyLevel.MEDIUM,

    var timeUntilDue: Long = 0,

    var workingTimeLeft: Long = 0,

    var effort: Int = 0,

    var reason: String = ""
)