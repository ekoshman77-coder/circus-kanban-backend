//package com.backend.todo_api.config
//
//import com.backend.todo_api.constants.AppConstants
//import com.backend.todo_api.data.entity.*
//import com.backend.todo_api.data.repository.*
//import com.backend.todo_api.model.ScopeType
//import org.springframework.boot.ApplicationArguments
//import org.springframework.boot.ApplicationRunner
//import org.springframework.core.annotation.Order
//import org.springframework.security.crypto.password.PasswordEncoder
//import org.springframework.stereotype.Component
//import jakarta.persistence.EntityManager
//import jakarta.transaction.Transactional
//
//@Component
//@Order(3) // 🎯 Läuft direkt NACH dem DatabaseSeeder
//class TestDataSeeder(
//    private val entityManager: EntityManager,
//    private val scopeRepository: ScopeRepository,
//    private val userRepository: UserRepository,
//    private val passwordEncoder: PasswordEncoder
//) : ApplicationRunner {
//
//    @Transactional
//    override fun run(args: ApplicationArguments) {
//        // Abbruch, falls schon Test-User da sind
//        if (userRepository.count() > 0) {
//            return
//        }
//
//        println("=== 🧪 STARTE TESTDATEN-SEEDER (Dev-Data) ===")
//
//        // 1. DEPARTMENT & SCOPE BESCHAFFEN
//        val departmentScope = scopeRepository.findByName(ScopeType.DEPARTMENT)
//            ?: throw IllegalStateException("DEPARTMENT Scope wurde nicht in der DB gefunden!")
//
//        // Admin-Department aus dem DatabaseSeeder holen
//        val adminDepartment = entityManager.createQuery(
//            "SELECT d FROM DepartmentEntity d WHERE d.name = :name", DepartmentEntity::class.java
//        ).setParameter("name", AppConstants.ADMIN_DEPARTMENT_NAME)
//            .singleResult
//
//        // IT-Department als Testumgebung anlegen
//        val itDepartment = DepartmentEntity(name = "IT-Department", defaultScope = departmentScope)
//        entityManager.persist(itDepartment)
//
//        // 2. PASSWORT HASHEN & USER ERZEUGEN
//        val encodedPassword = passwordEncoder.encode("Qq123456")
//        if (encodedPassword == null) {
//            return
//        }
//        val adminUser = UserEntity(
//            firstName = "Chef",
//            lastName = "Admin",
//            username = "admin",
//            password = encodedPassword,
//            departmentId = adminDepartment.id,
//            isApproved = true
//        ).also { entityManager.persist(it) }
//
//        val itUser = UserEntity(
//            firstName = "Dev",
//            lastName = "IT",
//            username = "it_user",
//            password = encodedPassword,
//            departmentId = itDepartment.id,
//            isApproved = true
//        ).also { entityManager.persist(it) }
//
//        // 3. NOTEN ANLEGEN (1 Admin, 1 IT)
//        val adminNote = NoteEntity(
//            userId = adminUser.id,
//            title = "Admin Strategie 2026",
//            content = "Ideen für Unternehmensstruktur",
//            colorType = "BLUE",
//            departmentId = adminDepartment.id
//        ).also { entityManager.persist(it) }
//
//        val itNote = NoteEntity(
//            userId = itUser.id,
//            title = "Refactoring Backend",
//            content = "Architektur und KI-Komponenten trennen",
//            colorType = "GREEN",
//            departmentId = itDepartment.id
//        ).also { entityManager.persist(it) }
//
//        // 4. PROJEKTE ANLEGEN (Aus den Noten heraus)
//        val adminProject = ProjectEntity(
//            userId = adminUser.id,
//            ideaId = adminNote.id,
//            title = "Projekt Admin-Optimierung",
//            area = "Management",
//            departmentId = adminDepartment.id
//        ).also { entityManager.persist(it) }
//
//        val itProject = ProjectEntity(
//            userId = itUser.id,
//            ideaId = itNote.id,
//            title = "Projekt KI-Integration",
//            area = "Entwicklung",
//            departmentId = itDepartment.id
//        ).also { entityManager.persist(it) }
//
//        // 5. PRIVATE TODOS
//        val testTodos = listOf(
//            TodoEntity(task = "Schlüssel für Büro nachbestellen", userId = adminUser.id, category = "Privat"),
//            TodoEntity(task = "Monatsabrechnung prüfen", userId = adminUser.id, category = "Finanzen"),
//            TodoEntity(task = "IDE Update installieren", userId = itUser.id, category = "Workstation"),
//            TodoEntity(task = "Dokumentation durchlesen", userId = itUser.id, category = "Lernen")
//        )
//        testTodos.forEach { entityManager.persist(it) }
//
//        println("✅ Testdaten erfolgreich generiert!")
//        println("🔑 Logins: 'admin_user' & 'it_user' (PW: Pass1234!)")
//        println("=== 🏁 TESTDATEN-SEEDING BEENDET ===")
//    }
//}