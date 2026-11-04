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

## PostgreSQL und Architektur

OrbStack muss laufen. Lokale Datenbank starten:

```sh
docker compose up -d --wait
./gradlew bootRun
```

PostgreSQL 17.11 speichert Daten im benannten Volume `postgres_data`.
`docker compose down` behält Daten; `docker compose down -v` löscht sie.
Lokale Zugangsdaten: Datenbank, Benutzer und Passwort jeweils `expensetracker`.
Die Entwicklungswerte sind nicht für einen öffentlichen Betrieb gedacht.
`DB_USERNAME`, `DB_PASSWORD` und `DB_URL` überschreiben die Anwendungskonfiguration.
Compose unterstützt `DB_PORT` (Standard 5432); bei Änderung auch `DB_URL` anpassen.

Der Controller verwendet weiterhin den Repository-Adapter. Dieser bildet zwischen
API-Records und einer getrennten JPA-Entity ab und verwaltet Schreibtransaktionen.
Spring Data JPA übernimmt Datenzugriffe. Liquibase erstellt das Schema; Hibernate
validiert es nur. NUMERIC bewahrt Dezimalpräzision ohne feste Nachkommastellenskala.
Es gibt keine Flyway-Abhängigkeit oder zusätzliche Service-Schicht.

`./gradlew test` startet PostgreSQL automatisch mit Testcontainers und führt auch
Liquibase aus. Keine manuell gestartete Datenbank ist nötig. OrbStack und beim ersten
Lauf Netzwerkzugriff für Images und Abhängigkeiten sind erforderlich. Ohne Docker
schlagen die Tests fehl. Die Testdatenbank verwendet nicht das Compose-Volume.

## Manueller Persistenztest über Anwendungsneustart

1. `docker compose up -d --wait` und `./gradlew bootRun` ausführen.
2. In einem zweiten Terminal die POST-Anfrage oben ausführen; ID und Antwort notieren.
3. Anwendung mit Ctrl+C beenden. PostgreSQL und Volume unverändert lassen.
4. Anwendung erneut mit `./gradlew bootRun` starten.
5. `curl -i http://localhost:8080/api/expenses/ID` mit der notierten ID ausführen:
   Erwartet sind HTTP 200 und identische ID, Beschreibung, Betrag und Datum.
6. Eine weitere Ausgabe erstellen: Sie muss eine neue ID erhalten. Optional Compose
   mit `docker compose down` und `docker compose up -d --wait` neu erstellen und GET
   wiederholen; das Volume bewahrt die Daten. Nicht `down -v` verwenden.

Authentifizierung, Filter und Pagination sind weiterhin nicht enthalten.
