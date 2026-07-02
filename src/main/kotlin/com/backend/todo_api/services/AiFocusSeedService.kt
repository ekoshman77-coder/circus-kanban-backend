package com.backend.todo_api.services

import com.backend.todo_api.providers.AiInitialSeedProvider
import com.backend.todo_api.data.entity.AiFocusSampleEntity
import com.backend.todo_api.data.repository.AiFocusSampleRepository
import com.backend.todo_api.model.AiContextType
import com.backend.todo_api.model.FocusType
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AiFocusSeedService(
    private val aiFocusSampleRepository: AiFocusSampleRepository
) : AiInitialSeedProvider<FocusType> {

    override fun getContextType(): AiContextType = AiContextType.TODO_FOCUS

    /**
     * Liest die künstlichen Trainingsdaten für den TrainManager aus der DB
     */
    override fun getInitialSeeds(): List<Pair<String, FocusType>> {
        return aiFocusSampleRepository.findAll().map { sampleEntity ->
            val focus = try {
                FocusType.valueOf(sampleEntity.focusType)
            } catch (e: Exception) {
                FocusType.LOW_FOCUS
            }
            sampleEntity.sample to focus
        }
    }

    /**
     * Automatische Grundausbildung: Füllt die DB beim Serverstart, falls sie leer ist.
     */
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun seedInitialTrainingData() {
        if (aiFocusSampleRepository.count() == 0L) {
            val startLehrbuch = listOf(
                // 🧠 High Focus Beispiele (Tiefe Konzentration nötig)
                AiFocusSampleEntity(sample = "Algorithmus optimieren und Refactoring", focusType = "HIGH_FOCUS"),
                AiFocusSampleEntity(sample = "Datenbank-Migration für Produktion schreiben SQL", focusType = "HIGH_FOCUS"),
                AiFocusSampleEntity(sample = "Komplexen Bug im Payment-System analysieren", focusType = "HIGH_FOCUS"),
                AiFocusSampleEntity(sample = "Architektur-Konzept für neues KI-Feature ausarbeiten", focusType = "HIGH_FOCUS"),

                // ☕ Low Focus Beispiele (Routine-Aufgaben / nebenbei machbar)
                AiFocusSampleEntity(sample = "Müll runterbringen und Küche aufräumen", focusType = "LOW_FOCUS"),
                AiFocusSampleEntity(sample = "Kaffee kochen neue Bohnen nachfüllen", focusType = "LOW_FOCUS"),
                AiFocusSampleEntity(sample = "E-Mails beantworten und Post sortieren", focusType = "LOW_FOCUS"),
                AiFocusSampleEntity(sample = "Schreibtisch aufräumen und Monitore abwischen", focusType = "LOW_FOCUS")
            )

            aiFocusSampleRepository.saveAll(startLehrbuch)
            println("🌱 KI-Grundausbildung: ${startLehrbuch.size} Fokus-Beispiele erfolgreich in 'todo_samples' gepflanzt!")
        }
    }
}