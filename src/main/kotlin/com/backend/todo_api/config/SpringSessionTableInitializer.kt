package com.backend.todo_api.config

import org.springframework.boot.CommandLineRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@Order(0) // Läuft VOR deinem PermissionInitializer
class SpringSessionTableInitializer(
    private val jdbcTemplate: JdbcTemplate
) : CommandLineRunner {

    override fun run(vararg args: String) {
        val createSessionTable = """
            CREATE TABLE IF NOT EXISTS spring_session (
                PRIMARY_ID CHAR(36) NOT NULL,
                SESSION_ID CHAR(36) NOT NULL,
                CREATION_TIME BIGINT NOT NULL,
                LAST_ACCESS_TIME BIGINT NOT NULL,
                MAX_INACTIVE_INTERVAL INT NOT NULL,
                EXPIRY_TIME BIGINT NOT NULL,
                PRINCIPAL_NAME VARCHAR(100),
                CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
            );
        """.trimIndent()

        val createSessionIndex = """
            CREATE UNIQUE INDEX IF NOT EXISTS SPRING_SESSION_IX1 ON spring_session (SESSION_ID);
        """.trimIndent()

        val createSessionAttrTable = """
            CREATE TABLE IF NOT EXISTS spring_session_attributes (
                SESSION_PRIMARY_ID CHAR(36) NOT NULL,
                ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
                ATTRIBUTE_BYTES BYTEA NOT NULL,
                CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
                CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES spring_session(PRIMARY_ID) ON DELETE CASCADE
            );
        """.trimIndent()

        jdbcTemplate.execute(createSessionTable)
        jdbcTemplate.execute(createSessionIndex)
        jdbcTemplate.execute(createSessionAttrTable)

        println("✅ [SpringSessionInitializer] Session-Tabellen automatisch überprüft/erstellt!")
    }
}