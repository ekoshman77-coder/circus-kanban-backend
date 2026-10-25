package com.backend.todo_api.data.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "sensitive_patterns")
class SensitivePatternEntity(
    @Id
    var id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    var patternValue: String = "", // Z.B. "password" oder ein Regex-String

    @Column(nullable = false)
    var patternType: String = "", // "EXACT" oder "REGEX"

    @Column(nullable = false)
    var reason: String = "", // Z.B. "PASSWORD", "TOKEN"

    @Column(nullable = false)
    var weight: Double = 0.5, // 🧠 Das KI-Gewicht! (Startet neutral bei 0.5)

    @Column(nullable = false)
    var approvalCount: Int = 0, // Wie oft vom Admin bestätigt

    @Column(nullable = false)
    var rejectionCount: Int = 0 // Wie oft vom Admin abgelehnt
)