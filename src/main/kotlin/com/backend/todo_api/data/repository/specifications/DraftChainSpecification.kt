package com.backend.todo_api.data.repository.specifications

import com.backend.todo_api.data.entity.DraftChainEntity
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification

object DraftChainSpecifications {

    fun isAssignedTo(
        userIds: List<String>,
        departmentIds: List<String>,
        projectIds: List<String>,
        isAdmin: Boolean
    ): Specification<DraftChainEntity> {
        return Specification { root, query, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            // 1. Direct User Assignment: targetType = 'USER' AND targetId IN (userIds)
            if (userIds.isNotEmpty()) {
                val userPredicate = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get<String>("targetType"), "USER"),
                    root.get<String>("targetId").`in`(userIds)
                )
                predicates.add(userPredicate)
            }

            // 2. Department Assignment: targetType = 'DEPARTMENT' AND targetId IN (departmentIds)
            if (departmentIds.isNotEmpty()) {
                val deptPredicate = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get<String>("targetType"), "DEPARTMENT"),
                    root.get<String>("targetId").`in`(departmentIds)
                )
                predicates.add(deptPredicate)
            }

            // 3. Project Assignment: targetType = 'PROJECT' AND targetId IN (projectIds)
            if (projectIds.isNotEmpty()) {
                val projectPredicate = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get<String>("targetType"), "PROJECT"),
                    root.get<String>("targetId").`in`(projectIds)
                )
                predicates.add(projectPredicate)
            }

            // 4. Admin Assignment: targetType = 'ADMINS'
            if (isAdmin) {
                val adminPredicate = criteriaBuilder.equal(
                    root.get<String>("targetType"), "ADMINS"
                )
                predicates.add(adminPredicate)
            }

            if (predicates.isEmpty()) {
                criteriaBuilder.disjunction() // Liefert leeres Ergebnis, falls keine Kontexte vorhanden
            } else {
                criteriaBuilder.or(*predicates.toTypedArray())
            }
        }
    }
}