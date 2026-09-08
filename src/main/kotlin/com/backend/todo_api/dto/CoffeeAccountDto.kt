package com.backend.todo_api.dto

data class CoffeeAccountDto(
    var balance: Float = 0f,
    var emoji: String = "👩‍💻",
    var role: String = "Teammitglied" // ☕ Der Fun-Titel für die Kaffeekasse
)