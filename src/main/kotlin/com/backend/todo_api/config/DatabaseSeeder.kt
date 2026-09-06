package com.backend.todo_api.config

import com.backend.todo_api.constants.AppConstants
import com.backend.todo_api.data.entity.DepartmentEntity
import com.backend.todo_api.data.repository.DepartmentSpecializationRepository
import com.backend.todo_api.data.repository.ScopeRepository
import com.backend.todo_api.model.DepartmentSpecializationType

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
    private val scopeRepository: ScopeRepository,
    private val specializationRepository: DepartmentSpecializationRepository
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        println("=== 🚀 STARTE DATENBANK-INITIALISIERUNGS-CHECK ===")

        val departmentCount = entityManager.createQuery("SELECT COUNT(d) FROM DepartmentEntity d", java.lang.Long::class.java)
            .singleResult.toLong()

        if (departmentCount == 0L) {
            println("ℹ️ Keine Abteilungen gefunden. Erzeuge die '${AppConstants.ADMIN_DEPARTMENT_NAME}'-Abteilung...")

            val scope = scopeRepository.findByName(ScopeType.DEPARTMENT)
                ?: throw IllegalStateException("COMPANY Scope wurde nicht in der DB gefunden!")

            val adminSpec = specializationRepository.findByName(DepartmentSpecializationType.ADMIN)
                ?: throw IllegalStateException("ADMIN Specialization wurde nicht in der DB gefunden!")

            // Admin-Abteilung erhält den COMPANY-Scope UND die ADMIN-Spezialisierung
            val adminDepartment = DepartmentEntity(
                name = AppConstants.ADMIN_DEPARTMENT_NAME,
                defaultScope = scope,
                specialization = adminSpec
            )
            entityManager.persist(adminDepartment)

            println("✅ '${AppConstants.ADMIN_DEPARTMENT_NAME}'-Abteilung mit Spezialisierung ADMIN angelegt.")
        } else {
            println("✅ Abteilungen existieren bereits in der Datenbank.")
        }

        val userCount = entityManager.createQuery("SELECT COUNT(u) FROM UserEntity u", java.lang.Long::class.java)
            .singleResult.toLong()

        if (userCount == 0L) {
            println("⚠️ WARNUNG: Das System ist komplett leer! Kein Admin vorhanden.")
            println("👉 System befindet sich im Setup-Modus. Die nächste Registrierung wird zum ADMIN_HEAD!")
        } else {
            println("✅ Bestehende User gefunden.")
        }

        println("=== 🏁 INITIALISIERUNGS-CHECK BEENDET ===")
    }
}