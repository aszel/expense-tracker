# Expense Tracker

Grundgerüst einer REST-API mit Java 21, Spring Boot 3.5.16 und Gradle 8.14.3
(Kotlin DSL). Basispaket: `de.example.expensetracker`.

## Voraussetzungen

- Vorhandenes JDK 21; `java -version` prüfen und gegebenenfalls `JAVA_HOME` setzen.
- Internetzugang beim ersten Build zum Laden von Gradle und Abhängigkeiten.

Der Gradle Wrapper ist enthalten; eine globale Gradle-Installation ist nicht nötig.
Zusätzliche JDKs werden nicht automatisch heruntergeladen.

## Tests

```sh
./gradlew test
```

Der Test lädt den Spring-Anwendungskontext und prüft den Health-Endpunkt mit MockMvc
auf HTTP 200, JSON-Inhaltstyp und `{"status":"UP"}`.
Testbericht: `build/reports/tests/test/index.html`.

## Anwendung starten

```sh
./gradlew bootRun
```

Die Anwendung läuft standardmäßig auf Port 8080. Beenden mit `Ctrl+C`.

```sh
curl -i http://localhost:8080/api/health
```

Erwartet: HTTP `200 OK`, Inhaltstyp `application/json` und:

```json
{"status":"UP"}
```

Der Endpunkt bestätigt, dass die Anwendung Anfragen verarbeitet; Datenbankprüfungen
sind noch nicht enthalten.

## Ausführbare JAR erstellen

```sh
./gradlew bootJar
java -jar build/libs/expense-tracker-0.0.1-SNAPSHOT.jar
```

Unter Windows entsprechend `gradlew.bat` verwenden.

## Umfang

Enthalten sind Spring Web, Validation und Spring Boot Test sowie Hauptklasse und
Health-Controller. Ausgabenverwaltung und PostgreSQL folgen in späteren Schritten.
