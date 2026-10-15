package com.backend.todo_api.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "draft_chains")
class DraftChainEntity(
    @Id
    var id: String = "",

    var title: String = "",

    var targetType: String? = null,
    var targetId: String? = null,

    var createdAt: Long = 0L,

    var assignedTo: String? = null,
    var createdByUserId: String = "",
    var delegatedByUserId: String? = null,

    @Column(columnDefinition = "TEXT")
    var note: String? = null,

    @Column(columnDefinition = "TEXT")
    var lastErrorReason: String = "",

    var lastErrorCode: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items_json", columnDefinition = "jsonb")
    val itemsJson: String = "", // oder dein DTO/Klassen-Typ
  )