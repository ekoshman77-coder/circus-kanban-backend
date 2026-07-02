package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.AiMilestoneKnowledgeEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AiMilestoneKnowledgeRepository : JpaRepository<AiMilestoneKnowledgeEntity, String> {

    // Holt alles KI-Wissen für den aktuellen User und die passenden Schlüsselwörter
    fun findByUserIdAndKeywordIn(userId: String, keywords: Collection<String>): List<AiMilestoneKnowledgeEntity>

    // Sucht einen ganz spezifischen Eintrag (wichtig für das Erhöhen des successCount oder Degrading)
    fun findByUserIdAndKeywordAndMilestoneTitle(userId: String, keyword: String, milestoneTitle: String): AiMilestoneKnowledgeEntity?
}