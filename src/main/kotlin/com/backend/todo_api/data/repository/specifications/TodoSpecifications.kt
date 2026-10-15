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
     * 🗑️ Archiv- & Papierkorb-Status (isArchived)
     */
    fun isArchived(isArchived: Boolean): Specification<TodoEntity> {
        return Specification { root, _, builder ->
            builder.equal(root.get<Boolean>("isArchived"), isArchived)
        }
    }

    /**
     * 📅 Erstellungszeitraum (fromDate .. toDate)
     */
    fun isCreatedBetween(fromDate: Long?, toDate: Long?): Specification<TodoEntity> {
        return Specification { root, _, builder ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()

            fromDate?.let {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), it))
            }
            toDate?.let {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), it))
            }

            if (predicates.isEmpty()) builder.conjunction() else builder.and(*predicates.toTypedArray())
        }
    }

    /**
     * 🏢 Firmen-Scope (COMPANY)
     */
    fun isCompanyScope(): Specification<TodoEntity> {
        return Specification { root, _, builder ->
            builder.and(
                builder.isNotNull(root.get<String>("milestoneId")),
                builder.notEqual(root.get<String>("milestoneId"), "")
            )
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
     * 📁 Projekt-Scope (PROJECT)
     * Ein User mit Projekt-Scope sieht alle Projekt-Todos,
     * die zu diesem spezifischen Projekt gehören (über MilestoneEntity -> Project.id).
     */
    fun isProjectScope(projectId: String): Specification<TodoEntity> {
        return Specification { root, query, builder ->
            // Subquery auf Meilensteine des Projekts
            val subquery = query.subquery(String::class.java)
            val milestoneRoot = subquery.from(com.backend.todo_api.data.entity.MilestoneEntity::class.java)

            val projectJoin = milestoneRoot.join<Any, Any>("project")

            // SELECT m.id FROM MilestoneEntity m JOIN m.project p WHERE p.id = :projectId
            subquery.select(milestoneRoot.get("id"))
                .where(builder.equal(projectJoin.get<String>("id"), projectId))

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