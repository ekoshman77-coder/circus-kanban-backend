# SyncFocus & ProjectHub – Backend Engine ⚙️
> Kotlin & Spring Boot Core Engine: Dynamische RBAC, JPA-Spezifikationen, Custom Security & Eigenentwickelte KI-Modelle

Dieses Repository enthält das **Backend** der Applikation. Es ist in **Kotlin** und **Spring Boot** implementiert und bietet eine skalierbare, hochperformante REST-API mit dynamischer Rechteverwaltung, komplexer JPA-Filterung, maßgeschneiderten Sicherheitsmechanismen sowie vollständig eigenständig entwickelten Machine-Learning-Algorithmen.

---

## 🏗️ Technologischer Stack
* **Sprache & Framework:** Kotlin, Java 21, Spring Boot 3.x
* **Datenbank & ORM:** Spring Data JPA, Hibernate, PostgreSQL / H2
* **Sicherheit & Auth:** Session-Management, Passwort-Verschlüsselung, Custom XSRF-Handler & Dynamic RBAC
* **Architektur:** Layered Clean Architecture (Controller -> Service -> Repository / Specification)
* **Machine Learning:** Custom Naive Bayes Engine & Custom Mini-Neural-Network (In-House Implementation)

---

## 📖 Backend-Architektur & Funktionsumfang

### 1. Eigenentwickelte KI-Engine (In-House AI Engine)

Im Gegensatz zu klassischen Systemen, die externe KI-APIs nutzen, verwendet dieses Backend zwei vollständig selbst entwickelte, leichtgewichtige Machine-Learning-Modelle für intelligentes Aufgabensortieren und Datenvalidierung.

#### 1.1. Naive Bayes Classifier (Wahrscheinlichkeitsmodell)
* **Algorithmus:** Text- und Merkmal-Klassifikation basierend auf dem Bayes-Theorem mit Laplace-Glättung (Laplace Smoothing).
* **Anwendungsbereiche:**
    * **Kategorisierung:** Automatische Zuweisung von Kategorien für Aufgaben und Ideen.
    * **Text-Anomalie-Erkennung (Sanitization):** Identifizierung von Testmüll, zufälligen Zeichenketten oder sensiblen Mustern in Beschreibungen.
    * **Quick-Tasks & Meilensteine:** Generierung von Vorschlägen für Projekt-Milestones und Schnellaufgaben.
    * **Prozedurale Gamification:** Dynamische Namensgenerierung für Benutzer-Level im Header.
* **Admin-in-the-Loop Training:** Über spezielle Admin-Endpunkte können neue Trainingsmuster (z. B. `CREATE_PATTERN`) eingelernt werden, um das Modell im laufenden Betrieb kontinuierlich zu verbessern.

#### 1.2. Mini-Neural Network (Kompaktes Neuronales Netz)
* **Eigenimplementierung:** Ein kompaktes Feedforward-Neuronales-Netzwerk in purem Kotlin (Eingabeschicht, Verborgene Schicht mit ReLU/Sigmoid-Aktivierung, Ausgabeschicht).
* **Anwendungsbereich:** Analyse nicht-linearer Zusammenhänge zwischen Prioritäten, Auslastungen, Aufwandsschätzungen und Zeitfenstern im `Smart Planner`.
* **Multi-Model Ensemble & Feedback Loop:**
    * Bayes-Modell und Neuronales Netz generieren parallel unabhängige Empfehlungen.
    * Benutzerentscheidungen (Accept/Decline) werden über Feedback-Endpunkte zurückgesendet und fließen direkt in den Trainings-Loop beider Modelle ein.

---

### 2. Custom Security, XSRF & Validierung

* **Eigenentwickelter XSRF-Handler:** Ein zu reinen Lern- und Verständniszwecken selbst geschriebener XSRF-Schutzmechanismus, der nach einem einfachen mathematischen Prinzip (z. B. der `a + 2`-Transformation) Tokens generiert und validiert.
* **Input Validation (`inputValidation`):** Umfassende Validierungslogik für sämtliche eingehenden Payloads, um fehlerhafte Daten und Injections frühzeitig abzufangen.
* **Passwort-Verschlüsselung:** Sichere kryptografische Hashing-Verfahren zum Schutz der Benutzerpasswörter in der Datenbank.
* **Session-Management:** Kontrollierte Sitzungsverwaltung für den produktiven Live-Betrieb.

---

### 3. Dynamische Datenbank-RBAC (Dynamic Security & Permissions)

Die Rechteverwaltung basiert nicht auf statischen Hardcoded-Rollen, sondern auf einer dynamischen Datenstruktur in der Datenbank:
* **Persistente Rechte-Matrix:** Rollen, Ressourcen, Scopes und spezifische Permissions sind vollständig in der Datenbank hinterlegt.
* **Admin-Verwaltung:** Über die Administrationsoberfläche können Rechte zur Laufzeit ohne Server-Neustart angepasst werden.
* **Granulare Absicherung:** Interceptoren und Guards prüfen eintreffende Anfragen gegen die dynamische Rechte-Matrix.
* **Sandbox & 403 Escalation:** Versucht ein Benutzer im *Labor Mode* oder ohne ausreichende Rechte administrative Aktionen auszuführen, antwortet der Server strikt mit `HTTP 403 Forbidden`. Diese fehlerhaften Anfragen werden auf Client-Seite als Drafts gesichert und können an Administratoren zur Genehmigung freigegeben werden.

---

### 4. Dynamische Abfragen mit Spring Data JPA Specifications

Für komplexe Such-, Sortier- und Filteroperationen (insbesondere im `Admin Todo Board`) nutzt das Backend das **Specification Pattern**:
* **AdminTodoSpecification:** Dynamischer Aufbau von SQL/JPQL-Prädikaten basierend auf optionalen Parametern (`departmentId`, `projectId`, `targetUserId`, `isArchived`, Datumsbereiche).
* **Direct Fetch Endpunkte:** Endpunkte wie `GET /api/admin/todos/filter` liefern hochoptimierte, gefilterte Datenmengen direkt aus der Datenbank für Admin-Konsolen, ohne den clientseitigen Queue-Puffer zu belasten.

---

### 5. Robustes Error-Handling & Validierung

* **Duplikatschutz (HTTP 409):** Bei Versuchen, bereits existierende Datensätze oder Muster erneut anzulernen, wirft das Backend eine `ResourceAlreadyExistsException`, die sauber in ein `HTTP 409 Conflict` übersetzt wird.
* **Transaktionale Sicherheit:** Alle schreibenden Operationen laufen in transaktionalen Kontexten (`@Transactional`), um Datenkonsistenz bei Batch-Queue-Verarbeitungen zu garantieren.

---

## 👥 Team & Projektbeteiligte

* **Elena Koshman** – Lead Developer & Software-Architektin
* **Gemini (Google)** – AI Collaborator & Co-Developer (Backend-Architektur, Algorithmen-Design & Code-Entwicklung)