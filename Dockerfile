# Schritt 1: Wir nutzen ein offizielles Image mit Java 17 zum Bauen
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Kopiere die Gradle-Dateien, um die Abhängigkeiten zu laden
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Kopiere den eigentlichen Quellcode
COPY src src

# Baue die Anwendung (wir überspringen die Tests für den schnellen Build)
RUN ./gradlew bootJar -x test

# Schritt 2: Wir bauen das schlanke finale Image
FROM eclipse-temurin:17-jre
WORKDIR /app

# Kopiere das fertige JAR-Paket aus dem ersten Schritt
COPY --from=build /app/build/libs/*.jar app.jar

# Öffne den Port 8080 nach außen
EXPOSE 8080

# Befehl, um das Backend im Container zu starten
ENTRYPOINT ["java", "-jar", "app.jar"]