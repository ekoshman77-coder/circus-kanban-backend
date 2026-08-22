## ☕ Vorlage für das Backend-Repository (`README.md`)

```markdown
# SyncFocus & ProjectHub – Backend ⚙️
> Reaktives Fullstack-Ökosystem für agiles Projekt- und Selbstmanagement

Dieses Repository enthält das **Backend** der Applikation, das als hochperformante, datengestützte Schnittstelle für das agile Projektmanagement, die Benutzerverwaltung und die KI-gestützte Smartplanung dient.

---

## 🏗️ Technologischer Stack (Backend)
* **Sprache:** Kotlin
* **Framework:** Spring Boot 4.x
* **API-Architektur:** RESTful Web Services (mit JSON-Schnittstellen)
* **Datenbank & ORM:** PostgreSQL & Spring Data JPA / Hibernate
* **Sicherheit & Auth:** Spring Security, JWT (JSON Web Tokens) & feingranulare Role/Scope-Prüfung
* **Machine Learning / KI:** Eigenimplementierte Algorithmen (Bayes-Klassifikator & kompaktes Neuronales Netz) in Kotlin

---

## 🧩 Backend-Highlights & Kern-Module

1. **🤖 Duales KI-Empfehlungs-System:**
   * **Naïve Bayes Klassifikator:** Statistisches Modell zur Analyse historischer Aufgabendaten und Prioritätsberechnung basierend auf Verfalleffekten und Wichtigkeiten.
   * **Kompaktes Neuronales Netz (Custom NN):** Eigenes Forward-Neural-Network zur parallelen Mustererkennung von Nutzerpräferenzen und Aufwandsabschätzungen.
   * **Vergleichs-API:** Stellt Ergebnisse beider Modelle bereit, sodass das Frontend dem Nutzer zwei transparente Vorschläge präsentieren kann.

2. **🔐 Admin-, Rechte- & Rollenverwaltung:**
   * Abbildung eines mehrstufigen Hierarchie-Modells (Abteilungen, Projektrollen, Ressourcen & Scopes).
   * Schnittstellen für den Benutzer-Approval-Prozess (Warteraum & Abteilungs-Zuweisung).

3. **📊 Datenaggregations-Engine für Statistik:**
   * Hochoptimierte DB-Abfragen zur Berechnung von Tagesauslastungen, Fokuszeiten (Pomodoro) und Retrospektiven-Analysen.

4. **💾 Datenkonsistenz & PostgreSQL:**
   * Robustes relationales Schema mit relationalen Mappings für Benutzer, Abteilungen, Berechtigungen, Aufgaben und Meilensteine.

---

## 🛠️ Installation & Start

### Voraussetzungen
Da das Backend auf modernsten Framework-Generationen aufbaut, werden folgende Versionen zwingend benötigt:
* **Java JDK:** v17 (über die Gradle-Toolchain definiert)
* **Kotlin:** v2.2.21
* **Spring Boot:** v4.0.6
* **Datenbank:** Eine laufende PostgreSQL-Datenbank

### Schritte
1. Repository klonen:
   ```bash
   git clone <https://github.com/ekoshman77-coder/circus-kanban-backend>
   cd <todo-api>
   
2. Datenbank konfigurieren:

Passe die Zugangsdaten in der application.properties (oder application.yml) an:

    spring.datasource.url=jdbc:postgresql://localhost:5432/syncfocus_db
    spring.datasource.username=postgres
    spring.datasource.password=dein_passwort

3. Anwendung bauen und starten:

    ./gradlew bootRun
    
🧪 Testing & Build

    Unit- & Integrationstests ausführen:
    
        ./gradlew test
        
    Production JAR erstellen:
    
        ./gradlew build
    
    
👥 Team & Projektbeteiligte

    Elena Koshman – Lead Developer & Software-Architektin
    Gemini (Google) – Co-Developer (Architektur, KI-Logik & Code-Entwicklung)
