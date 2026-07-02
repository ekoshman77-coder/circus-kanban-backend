package com.backend.todo_api.data.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

// 2. Die Kaffeekassen-Zusatztabelle
@Entity
@Table(name = "coffee_accounts")
data class CoffeeAccountEntity(
    @Id var userId: String = "", // Identisch mit der User-ID (1-zu-1 Beziehung)
    var balance: Float = 0.0f,
    var emoji: String = "👩‍💻",
    var role: String = "Teammitglied"
)