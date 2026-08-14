package com.backend.todo_api.data.repository

import com.backend.todo_api.data.entity.NeuronEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NeuronRepository: JpaRepository<NeuronEntity, String> {
}