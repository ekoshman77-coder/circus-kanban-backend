package com.backend.todo_api.config

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.model.ScopeType
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.core.annotation.Order

@Component
@Order(2)
class DatabaseSeeder(
    private val entityManager: EntityManager,
    private val scopeRepository: ScopeRepository
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        println("=== 🚀 STARTE DATENBANK-INITIALISIERUNGS-CHECK ===")

        // 1. Prüfen, ob überhaupt Abteilungen existieren
        val departmentCount = entityManager.createQuery("SELECT COUNT(d) FROM DepartmentEntity d", java.lang.Long::class.java)
            .singleResult.toLong()

        if (departmentCount == 0L) {
            println("ℹ️ Keine Abteilungen gefunden. Erzeuge die '${AppConstants.ADMIN_DEPARTMENT_NAME}'-Abteilung...")

            val companyScope = scopeRepository.findByName(ScopeType.COMPANY)
                ?: throw IllegalStateException("COMPANY Scope wurde nicht in der DB gefunden!")

            // Die Admin-Abteilung bekommt als Basis-Scope den COMPANY-Scope
            val adminDepartment = DepartmentEntity(name = AppConstants.ADMIN_DEPARTMENT_NAME, defaultScope = companyScope)
            entityManager.persist(adminDepartment)

            println("✅ '${AppConstants.ADMIN_DEPARTMENT_NAME}'-Abteilung erfolgreich mit ID ${adminDepartment.id} angelegt.")
        } else {
            println("✅ Abteilungen existieren bereits in der Datenbank.")
        }

        // 2. Prüfen, ob es überhaupt User im System gibt
        val userCount = entityManager.createQuery("SELECT COUNT(u) FROM UserEntity u", java.lang.Long::class.java)
            .singleResult.toLong()

        if (userCount == 0L) {
            println("⚠️ WARNUNG: Das System ist komplett leer! Kein Admin vorhanden.")
            println("👉 System befindet sich im Setup-Modus. Die nächste Registrierung wird zum ADMIN_HEAD!")
        } else {
            println("✅ Bestehende User gefunden. Der Türsteher-Modus ist voll aktiv.")
        }

        println("=== 🏁 INITIALISIERUNGS-CHECK BEENDET ===")
    }
}