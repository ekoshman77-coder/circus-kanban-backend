package com.backend.todo_api.services

import com.backend.todo_api.data.repository.MilestoneRepository
import com.backend.todo_api.dto.MilestoneDto
import com.backend.todo_api.dto.toEntity
import com.backend.todo_api.exceptions.MilestoneNotFoundException
import org.springframework.stereotype.Service
import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull

@Service
class MilestoneService(val milestoneRepository: MilestoneRepository) {
    @Transactional
    fun recalculateMilestoneProgress(
        milestoneId: String?,
        effort: Int,
        usedEffort: Int,
        isDone: Boolean
    ) {
        if (milestoneId.isNullOrBlank()) return

        val milestone = milestoneRepository.findById(milestoneId).orElse(null) ?: return

        // Wir nutzen dein existierendes 'usedDuration'-Feld!
        // Hier tracken wir die Summe des Aufwands der erledigten To-Dos.
        if (isDone) {
            // Wenn die Aufgabe erledigt wurde, rechnen wir den Aufwand (z.B. das geplante effort) hinzu
            milestone.usedDuration += effort.toDouble()
        } else {
            // Wenn das Häkchen weggenommen wird, ziehen wir es wieder ab
            milestone.usedDuration -= effort.toDouble()
            if (milestone.usedDuration < 0.0) {
                milestone.usedDuration = 0.0
            }
        }

        milestoneRepository.save(milestone)
    }

    @Transactional
    fun createMilestone(dto: MilestoneDto): MilestoneDto {
        val entity = dto.toEntity()
        val saved = milestoneRepository.save(entity)
        return saved.toDto()
    }

    fun getMilestoneById(id: String): MilestoneDto {
        val milestone = milestoneRepository.findByIdOrNull(id)
            ?: throw MilestoneNotFoundException("Meilenstein mit ID $id wurde nicht gefunden")
        return milestone.toDto()
    }

    // 🔄 NEU: Update über unseren Service (Hier können wir später Meilenstein-XP prüfen!)
    @Transactional
    fun updateMilestone(id: String, dto: MilestoneDto): MilestoneDto? {
        if (!milestoneRepository.existsById(id)) return null

        dto.id = id
        val entity = dto.toEntity()
        val saved = milestoneRepository.save(entity)

        // 🎮 PLATZHALTER FÜR MORGEN:
        // Wenn saved.status == "Erledigt", triggere fettes Gamification-Event!

        return saved.toDto()
    }

    // ❌ NEU: Löschen über unseren Service
    @Transactional
    fun deleteMilestone(id: String): Boolean {
        return if (milestoneRepository.existsById(id)) {
            milestoneRepository.deleteById(id)
            true
        } else {
            false
        }
    }
}

