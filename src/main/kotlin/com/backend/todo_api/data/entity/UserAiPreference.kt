package com.backend.todo_api.data.entity

import jakarta.persistence.*

@Entity
@Table(name = "user_ai_preferences")
class UserAiPreference(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    @Column(name = "user_energy", nullable = false)
    val userEnergy: String = "", // "low", "medium", "high", "any"

    @Column(name = "preference_type", nullable = false)
    val preferenceType: String = "", // "EFFORT", "TIME", "MOTIVATION"

    @Column(name = "preference_value", nullable = false)
    val preferenceValue: String = "", // "aufwendig", "leicht", "lang", "Tag:Doku" etc.

    @Column(nullable = false)
    var score: Int = 0 // die Belohnungs- oder Strafpunkte!
)