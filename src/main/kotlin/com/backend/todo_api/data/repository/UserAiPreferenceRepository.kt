package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.UserAiPreference
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserAiPreferenceRepository : JpaRepository<UserAiPreference, Long> {

    // Holt alle gelernten Punkte für einen bestimmten Benutzer
    fun findByUserId(userId: String): List<UserAiPreference>

    // Sucht eine ganz gezielte Kombination (wichtig fürs Updaten beim Feedback!)
    fun findByUserIdAndUserEnergyAndPreferenceTypeAndPreferenceValue(
        userId: String,
        userEnergy: String,
        preferenceType: String,
        preferenceValue: String
    ): UserAiPreference?
}