package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.DraftChainEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository

@Repository
interface DraftChainRepository : JpaRepository<DraftChainEntity, String>, JpaSpecificationExecutor<DraftChainEntity> {
    fun findByAssignedTo(userId: String): List<DraftChainEntity>
}