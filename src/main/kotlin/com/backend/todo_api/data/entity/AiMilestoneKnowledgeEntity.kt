package com.backend.todo_api.data.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "ai_milestone_knowledge",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["userId", "keyword", "milestoneTitle"]) // 🛡️ Datenbank-Sperre gegen Duplikate!
    ]
)
class AiMilestoneKnowledgeEntity(
    @Id var id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val keyword: String = "",
    val milestoneTitle: String = "",
    var scores: Int = 10,
)

