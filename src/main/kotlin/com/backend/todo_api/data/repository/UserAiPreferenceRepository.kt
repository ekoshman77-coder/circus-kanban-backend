package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.UserAiPreference
import com.backend.todo_api.model.EnergyLevel
import com.backend.todo_api.model.PreferenceType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserAiPreferenceRepository : JpaRepository<UserAiPreference, String> {

    // Holt alle gelernten Punkte für einen bestimmten Benutzer
    fun findByUserId(userId: String): List<UserAiPreference>

    // Sucht eine ganz gezielte Kombination (wichtig fürs Updaten beim Feedback!)
    fun findByUserIdAndUserEnergyAndPreferenceTypeAndPreferenceValue(
        userId: String,
        userEnergy: EnergyLevel,
        preferenceType: PreferenceType,
        preferenceValue: String
    ): UserAiPreference?
}