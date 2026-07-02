package com.backend.todo_api.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@CrossOrigin(origins = ["http://localhost:4200"])
@RestController
@RequestMapping("/api/health")
class HealthController {

    @GetMapping
    fun checkHealth(): ResponseEntity<String> {
        return ResponseEntity.ok("OK")
    }
}