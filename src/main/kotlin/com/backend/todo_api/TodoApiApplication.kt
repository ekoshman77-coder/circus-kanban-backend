package com.backend.todo_api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class TodoApiApplication

fun main(args: Array<String>) {
	runApplication<TodoApiApplication>(*args)
}
