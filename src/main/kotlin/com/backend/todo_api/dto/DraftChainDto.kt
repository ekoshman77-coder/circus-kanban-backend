package com.backend.todo_api.dto

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class DelegationTargetDto(
    @field:NotBlank(message = "Target-Type darf nicht leer sein")
    val type: String, // z.B. "USER", "DEPARTMENT", "PROJECT", "ADMINS"

    val id: String? = null // Kann null sein, wenn type == "ADMINS"
)

data class QueueItemDto(
    @field:NotBlank(message = "Queue-ID darf nicht leer sein")
    val id: String,

    @field:NotBlank(message = "serviceName darf nicht leer sein")
    val serviceName: String,

    @field:NotBlank(message = "action darf nicht leer sein")
    val action: String,

    val timestamp: Long = System.currentTimeMillis(),

    // Payload kann dynamisch je nach Handler/Action sein
    val payload: Map<String, Any>? = null
)

data class DraftChainItemDto(
    @JsonProperty("isRootCause")
    @get:JsonProperty("isRootCause")
    @JsonAlias("rootCause") // Liest auch bereits gespeicherte DB-Einträge mit "rootCause"
    val isRootCause: Boolean = false,

    @field:NotNull(message = "queueItem darf nicht null sein")
    @field:Valid
    val queueItem: QueueItemDto
)

data class DraftChainDto(
    val id: String,
    val title: String,
    val target: DelegationTargetDto? = null,
    val createdAt: Long, // Epoch millis im Frontend
    val assignedTo: String? = null,
    val createdByUserId: String,
    val delegatedByUserId: String? = null,
    val note: String? = null,
    val lastErrorReason: String,
    val lastErrorCode: Int,
    val items: List<DraftChainItemDto> = emptyList()
)

data class DelegateDraftDto(
    @field:NotBlank(message = "Entwurf-ID darf nicht leer sein")
    val id: String,

    @field:NotBlank(message = "Titel darf nicht leer sein")
    val title: String,

    @field:NotNull(message = "Ziel (target) muss angegeben werden")
    @field:Valid // Wichtig: Validiert auch die Felder im verschachtelten DelegationTargetDto!
    val target: DelegationTargetDto,

    val createdAt: Long = System.currentTimeMillis(),

    @field:NotBlank(message = "Ersteller-ID darf nicht leer sein")
    val createdByUserId: String,

    val lastErrorReason: String? = null,
    val lastErrorCode: Int? = null,
    val items: List<DraftChainItemDto> = emptyList()
)