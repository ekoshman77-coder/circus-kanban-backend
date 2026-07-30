package com.backend.todo_api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestHandler // Das Interface
import org.springframework.security.web.csrf.DefaultCsrfToken
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import java.util.function.Supplier

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): BCryptPasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // 1. CORS
            .cors { cors ->
                val source = UrlBasedCorsConfigurationSource()
                val config = CorsConfiguration()
                config.allowedOrigins = listOf("http://localhost:4200")
                config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                config.allowedHeaders = listOf("Authorization", "Cache-Control", "Content-Type", "X-XSRF-TOKEN")
                config.exposedHeaders = listOf("X-XSRF-TOKEN")
                config.allowCredentials = true
                source.registerCorsConfiguration("/**", config)
                cors.configurationSource(source)
            }

            // 2. CSRF - Mit unserem eigenen Plaintext-Handler
            .csrf { csrf ->
                csrf
                    .ignoringRequestMatchers("/api/users/**", "/api/health") // Login/Register ignorieren
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // Cookie lesbar machen
                    .csrfTokenRequestHandler(PlaintextCsrfTokenRequestHandler()) // 👈 Unser neuer Handler!
            }

            // 3. Filter, der das Cookie beim ersten Laden erzwingt
            .addFilterAfter(object : OncePerRequestFilter() {
                override fun doFilterInternal(
                    request: HttpServletRequest,
                    response: HttpServletResponse,
                    filterChain: FilterChain
                ) {
                    val csrfToken = request.getAttribute(CsrfToken::class.java.name) as? CsrfToken
                    csrfToken?.token // Erzwingt das Schreiben des Cookies
                    filterChain.doFilter(request, response)
                }
            }, BasicAuthenticationFilter::class.java)

            //  DAS HELMET-ÄQUIVALENT: Sicherheits-Header für XSS- und Clickjacking-Schutz
            .headers { headers ->
                headers
                    // 🛡️ Wir packen den String in die von Spring erwartete HeaderValue-Box!
                    .xssProtection { it.headerValue(XXssProtectionHeaderWriter.HeaderValue.from( "1; mode=block")) }

                    // Verhindert Clickjacking
                    .frameOptions { it.deny() }

                    // Schaltet Content-Type-Sniffing ab
                    .contentTypeOptions { }
            }
            // 4. Berechtigungen
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }

        return http.build()
    }
}

/**
 * 🎪 UNSER EXPERIMENTELLES CHIFFRE-LABOR ("Kinderverschlüsselung" Buchstabe + 1)
 */
private class PlaintextCsrfTokenRequestHandler : CsrfTokenRequestHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        csrfToken: Supplier<CsrfToken>
    ) {
        // 1. Wir holen das echte, originale Token von Spring Security
        val originalToken = csrfToken.get()

        // 2. Wir jagen den Token-String durch unsere "Buchstabe + 1" Maschine
        val encryptedTokenValue = encrypt(originalToken.token)

        // 3. Wir bauen ein manipuliertes Token-Objekt für Angular zusammen.
        // Das ist das Token, das Spring in den Cookie schreibt!
        val fakeTokenForFrontend = DefaultCsrfToken(
            originalToken.headerName,
            originalToken.parameterName,
            encryptedTokenValue // 👈 Angular sieht NUR den verschlüsselten Wert!
        )

        // 4. Für die interne Validierung von Spring Security:
        // Wenn Angular den verschlüsselten Wert im Header mitschickt, holt Spring Security
        // das Erwartungsmuster aus den Request-Attributen.
        // Wenn wir hier ebenfalls das fakeToken reinlegen, erwartet Spring genau den verschlüsselten Wert.
        // Das bedeutet: Angular schickt "verschlüsselt", Spring vergleicht es mit "verschlüsselt" – und es klappt reibungslos!
        request.setAttribute(CsrfToken::class.java.name, fakeTokenForFrontend)
        request.setAttribute(originalToken.parameterName, fakeTokenForFrontend)
    }

    // 🔮 Hilfsfunktion: Verschiebt jeden Buchstaben im Token-String um +1
    private fun encrypt(input: String): String {
        return input.map { char ->
            if (char.isLetterOrDigit()) (char.code + 2).toChar() else char
        }.joinToString("")
    }
}