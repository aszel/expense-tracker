# Expense Tracker

REST-API mit Java 21, Spring Boot 3.5.16 und Gradle 8.14.3
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

Die Tests laden den Spring-Anwendungskontext und prüfen mit MockMvc Health,
den CRUD-Ablauf, serverseitige IDs, Dezimalpräzision, Validierung und unbekannte IDs
sowie Problem Details für ungültige Eingaben.
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

## Ausgaben verwalten

Eine Ausgabe hat `id`, `description`, `amount` und `date`. Die ID wird vom Server
vergeben. Beschreibung darf weder fehlen noch leer sein, `amount` ist eine positive
`BigDecimal`-Zahl und `date` ein Pflichtdatum im Format `YYYY-MM-DD`.
Es gilt zunächst eine gemeinsame, nicht separat gespeicherte Währung.

```sh
# Erstellen: 201 Created, Location-Header verweist auf die neue Ausgabe
curl -i -X POST http://localhost:8080/api/expenses \
  -H 'Content-Type: application/json' \
  -d '{"description":"Mittagessen","amount":12.34,"date":"2026-11-03"}'

# Alle Ausgaben: 200 OK (anfangs [])
curl -i http://localhost:8080/api/expenses

# Einzelne Ausgabe: 200 OK oder 404
curl -i http://localhost:8080/api/expenses/1

# Vollständig aktualisieren: 200 OK oder 404; ID bleibt unverändert
curl -i -X PUT http://localhost:8080/api/expenses/1 \
  -H 'Content-Type: application/json' \
  -d '{"description":"Abendessen","amount":25.75,"date":"2026-11-04"}'

# Löschen: 204 No Content oder 404
curl -i -X DELETE http://localhost:8080/api/expenses/1

# Validierungsfehler: 400 Bad Request
curl -i -X POST http://localhost:8080/api/expenses \
  -H 'Content-Type: application/json' \
  -d '{"description":"","amount":0,"date":null}'
```

Die Beispiel-ID `1` gegebenenfalls durch die ID aus der POST-Antwort ersetzen.
PUT erstellt keine fehlenden Ausgaben. Die Liste ist nach ID aufsteigend sortiert.

Fehler liefern `application/problem+json` mit `type`, `title`, `status`, `detail`
und `instance`. Bei Feldvalidierungsfehlern enthält `errors` zusätzlich Feldnamen
und Meldungen. Auch unlesbares JSON, ungültige Datumswerte und ungültige IDs im Pfad
liefern HTTP 400.

## Architektur und Grenzen

Das Paket `expense` enthält Controller, Request-/Response-Records und ein konkretes
In-Memory-Repository. Bean Validation prüft Eingaben. Das zentrale Controller Advice
im Paket `error` verwendet Spring Problem Details. Eine zusätzliche Service-Schicht
ist für die aktuelle CRUD-Logik nicht nötig.

Das Repository synchronisiert Zugriffe und liefert unveränderliche Datensätze und
Listenkopien zurück. Daten und ID-Zähler gelten nur für diesen Anwendungsprozess und
gehen beim Neustart verloren. PostgreSQL, Authentifizierung, Filter und Pagination
sind noch nicht enthalten.
