package com.backend.todo_api.providers

interface SanitizationTargetProvider {
    /**
     * Eindeutiger Bezeichner des Ziels (z. B. "TODO", "PROJECT", "NOTE")
     */
    fun getTargetType(): String

    /**
     * Führt einen vollständigen Re-Scan aller aktiven Entitäten dieses Typs durch.
     */
    fun rescanAll()
}