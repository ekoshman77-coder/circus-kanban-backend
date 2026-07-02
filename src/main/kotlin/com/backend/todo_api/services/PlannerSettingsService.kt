package com.backend.todo_api.services

import com.backend.todo_api.data.repository.PlannerSettingsRepository
import com.backend.todo_api.dto.PlannerSettingsDto
import com.backend.todo_api.dto.toDto
import com.backend.todo_api.exceptions.UserDeletedException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class PlannerSettingsService (
    private val pannerSettingsRepository: PlannerSettingsRepository
) {

    fun update(settingsDto: PlannerSettingsDto): PlannerSettingsDto {
        // 1. Lade das ECHTE Objekt aus der DB inklusive der User-Verknüpfung
        val existingSettings = pannerSettingsRepository.findByIdOrNull(settingsDto.userId)
            ?: throw UserDeletedException("Settings not found for user ${settingsDto.userId}")

        // 2. Wir überschreiben nur die Werte, die sich ändern dürfen!
        // .apply ist pure Kotlin-Magie: Es arbeitet direkt im Objekt
        existingSettings.apply {
            defaultWorkingHours = settingsDto.defaultWorkingHours
            primeTimeStartHour = settingsDto.primeTimeStartHour
            primeTimeEndHour = settingsDto.primeTimeEndHour
        }

        // 3. Speichern und zurück als DTO an den Controller senden
        return pannerSettingsRepository.save(existingSettings).toDto()
    }

    fun getSettingsForUser(userId: String): PlannerSettingsDto {
        val settings = pannerSettingsRepository.findByIdOrNull(userId)
            ?: throw UserDeletedException("Settings not found for user $userId")
        return settings.toDto()
    }
}