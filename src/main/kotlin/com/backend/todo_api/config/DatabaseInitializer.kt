//package com.backend.todo_api.config
//
//import com.backend.todo_api.data.entity.DbVersionEntity
//import com.backend.todo_api.data.entity.LevelEntity
//import com.backend.todo_api.data.repository.DbVersionRepository
//import com.backend.todo_api.data.repository.LevelRepository
//import jakarta.transaction.Transactional
//import org.springframework.boot.CommandLineRunner
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Configuration
//import org.springframework.stereotype.Component
//
//@Component
//class DatabaseInitializer {
//
//    // 🌟 Hier definierst du die aktuelle Version deines Level-Schemas im Code
//    private val CURRENT_VERSION = 1
//
//    @Bean
//    @Transactional
//    fun initLevels(
//        levelRepository: LevelRepository,
//        dbVersionRepository: DbVersionRepository
//    ) = CommandLineRunner {
//
//        // 1. Hole die Version aus der DB (falls nicht vorhanden, starte bei Version 0)
//        val dbVersionEntry = dbVersionRepository.findById("LEVEL_SCHEMA")
//            .orElseGet { DbVersionEntity(version = 0) }
//
//        // 2. Prüfen: Ist die Version im Code neuer als die in der Datenbank?
//        if (CURRENT_VERSION > dbVersionEntry.version) {
//            println("🔄 DB-Version veraltet (${dbVersionEntry.version} < $CURRENT_VERSION). Starte Level-Update...")
//
//            // 3. Unsere gewünschten Levels (der Soll-Zustand für Version 1)
//            val defaultLevels = listOf(
//                LevelEntity(level = 1, requiredXp = 0, title = "To-Do-Lehrling 👶"),
//                LevelEntity(level = 2, requiredXp = 100, title = "Task-Manager 📋"),
//                LevelEntity(level = 3, requiredXp = 250, title = "Fokus-Meister 🧘"),
//                LevelEntity(level = 4, requiredXp = 500, title = "Produktivitäts-Monster 👹"),
//                LevelEntity(level = 5, requiredXp = 1000, title = "Code-Ninja 🥷"),
//                LevelEntity(level = 6, requiredXp = 2000, title = "Software-Architekt-Guru 👑")
//            )
//
//            // 4. Levels speichern (Überschreibt bestehende, fügt neue hinzu)
//            levelRepository.saveAll(defaultLevels)
//
//            // 5. Die neue Version in der Datenbank verewigen
//            dbVersionEntry.version = CURRENT_VERSION
//            dbVersionRepository.save(dbVersionEntry)
//
//            println("✅ Level-Tabelle erfolgreich auf Version $CURRENT_VERSION aktualisiert!")
//        } else {
//            // Wenn die Versionen gleich sind (oder die DB neuer ist), passiert absolut GAR NICHTS.
//            println("😴 Level-Tabelle ist auf dem neuesten Stand (Version $CURRENT_VERSION). Keine Aktion erforderlich.")
//        }
//    }
//}