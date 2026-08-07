package com.backend.todo_api.data.repository.specifications

import com.backend.todo_api.data.entity.TodoEntity
import org.springframework.data.jpa.domain.Specification

object TodoSpecifications {

    /**
     * 🛡️ Zeitfenster- & Archiv-Filter
     * Schließt archivierte Todos aus UND filtert erledigte Aufgaben heraus,
     * die älter als das cutoffDate sind.
     */
    fun isNotArchivedAndWithinCutoff(cutoffDate: Long): Specification<TodoEntity> {
        return Specification { root, _, builder ->
            val isNotArchived = builder.equal(root.get<Boolean>("isArchived"), false)

            val isNotDone = builder.equal(root.get<Boolean>("done"), false)
            val hasNoCompletedDate = builder.isNull(root.get<Long>("completedAt"))
            val isRecentlyCompleted = builder.greaterThan(root.get<Long>("completedAt"), cutoffDate)

            // (done == false OR completedAt IS NULL OR completedAt > cutoffDate)
            val timeCondition = builder.or(isNotDone, hasNoCompletedDate, isRecentlyCompleted)

            builder.and(isNotArchived, timeCondition)
        }
    }

    /**
     * 🏢 Firmen-Scope (COMPANY)
     */
    fun isCompanyScope(): Specification<TodoEntity> {
        return Specification { _, _, builder ->
            builder.conjunction() // Keine Einschränkung auf Scope-Ebene
        }
    }

    /**
     * 🏢 Abteilungs-Scope (DEPARTMENT)
     * Ein User mit Abteilungs-Scope sieht alle Projekt-Todos,
     * deren Projekt zu dieser Abteilung gehört (Project.departmentId).
     */
    fun isDepartmentScope(departmentId: String): Specification<TodoEntity> {
        return Specification { root, query, builder ->
            // Subquery auf Meilensteine
            val subquery = query.subquery(String::class.java)
            val milestoneRoot = subquery.from(com.backend.todo_api.data.entity.MilestoneEntity::class.java)

            // JOIN von Milestone zu Project (da MilestoneEntity die Beziehung 'project' hat)
            val projectJoin = milestoneRoot.join<Any, Any>("project")

            // SELECT m.id FROM MilestoneEntity m JOIN m.project p WHERE p.departmentId = :departmentId
            subquery.select(milestoneRoot.get("id"))
                .where(builder.equal(projectJoin.get<String>("departmentId"), departmentId))

            // WHERE todo.milestoneId IN (subquery)
            root.get<String>("milestoneId").`in`(subquery)
        }
    }

    /**
     * 👤 Ressourcen- / Privater Scope (RESOURCE)
     */
    fun isPrivateResourceScope(userId: String): Specification<TodoEntity> {
        return Specification { root, _, builder ->
            builder.and(
                builder.equal(root.get<String>("userId"), userId),
                builder.or(
                    builder.isNull(root.get<String>("milestoneId")),
                    builder.equal(root.get<String>("milestoneId"), "")
                )
            )
        }
    }
}