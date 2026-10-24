package com.backend.todo_api.services.sanitization

import com.backend.todo_api.dto.CreatePatternFromSelectionDto
import com.backend.todo_api.exceptions.ActionForbiddenException
import com.backend.todo_api.providers.SanitizationTargetProvider
import com.backend.todo_api.services.PermissionService
import com.backend.todo_api.services.sanitization.SanitizationService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class SanitizationOrchestrator(
    private val sanitizationService: SanitizationService,
    private val targetProviders: List<SanitizationTargetProvider>,
    private val permissionService: PermissionService
) {

    @Transactional
    fun createPatternFromSelectionAndRescan(userId: String, dto: CreatePatternFromSelectionDto) {
        // 1. Neues Pattern im SanitizationService extrahieren und speichern
        if (!permissionService.isAdmin(userId)) {
            throw ActionForbiddenException("Nur Admins dürfen globale Sensitivitäts-Muster erstellen.")
        }

        sanitizationService.createPatternFromSelection(dto)

        // 2. Alle registrierten Datenquellen (To-Dos, später Projekte, Notizen etc.) neu bewerten
        for (provider in targetProviders) {
            provider.rescanAll()
        }
    }
}