package com.backend.todo_api.data.entity

import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.PreferenceType
import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "user_ai_preferences")
class UserAiPreference(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "user_energy", nullable = false)
    val userEnergy: EnergyLevel = EnergyLevel.ANY,

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_type", nullable = false)
    val preferenceType: PreferenceType,

    @Column(name = "preference_value", nullable = false)
    val preferenceValue: String = "", // "aufwendig", "leicht", "lang", "Tag:Doku" etc.

    @Column(nullable = false)
    var score: Int = 0 // die Belohnungs- oder Strafpunkte!
)