package com.backend.todo_api.mapper

import com.backend.todo_api.data.entity.DraftChainEntity
import com.backend.todo_api.dto.DelegateDraftDto
import com.backend.todo_api.dto.DelegationTargetDto
import com.backend.todo_api.dto.DraftChainDto
import com.backend.todo_api.dto.DraftChainItemDto
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.KotlinModule

private val objectMapper: ObjectMapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

fun DraftChainEntity.toDto(): DraftChainDto {
    val target = if (this.targetType != null) {
        DelegationTargetDto(type = this.targetType!!, id = this.targetId)
    } else null

    val itemsList: List< DraftChainItemDto> = if (!this.itemsJson.isNullOrBlank()) {
        try {
            objectMapper.readValue(
                this.itemsJson,
                object : TypeReference< List< DraftChainItemDto>>() {}
            )
        } catch (e: Exception) {
            emptyList< DraftChainItemDto>()
        }
    } else {
        emptyList< DraftChainItemDto>()
    }

    return DraftChainDto(
        id = this.id,
        title = this.title,
        target = target,
        createdAt = this.createdAt,
        assignedTo = this.assignedTo,
        createdByUserId = this.createdByUserId,
        delegatedByUserId = this.delegatedByUserId,
        note = this.note,
        lastErrorReason = this.lastErrorReason,
        lastErrorCode = this.lastErrorCode,
        items = itemsList
    )
}

fun DelegateDraftDto.toEntity(currentUserId: String): DraftChainEntity {
    val itemsJsonString: String = try {
        objectMapper.writeValueAsString(this.items)
    } catch (e: Exception) {
        "[]"
    }

    return DraftChainEntity(
        id = this.id,
        title = this.title,
        targetType = this.target.type,
        targetId = this.target.id,
        createdAt = this.createdAt,
        assignedTo = null, // In DelegateDraftDto nicht vorhanden
        createdByUserId = this.createdByUserId,
        delegatedByUserId = currentUserId,
        note = null, // In DelegateDraftDto nicht vorhanden
        lastErrorReason = this.lastErrorReason ?: "",
        lastErrorCode = this.lastErrorCode ?: 0,
        itemsJson = itemsJsonString
    )
}