# Projektleitfaden

## Bestehende Projektkonventionen

- Java 21, Spring Boot 3.5.x (aktuell 3.5.16), Gradle Wrapper mit Kotlin DSL.
  Vorhandenes JDK verwenden; automatischer JDK-Download ist deaktiviert.
  Abhängigkeitsversionen verwaltet das Spring-Boot-BOM.
- Basispaket: `de.example.expensetracker`, nach Fachlichkeit gegliedert
  (`expense`, `health`, zentrale Fehlerbehandlung in `error`).
- Datenfluss: `ExpenseController` → `ExpenseRepository` (JPA-Adapter) →
  `ExpenseJpaRepository` → PostgreSQL. Keine zusätzliche Service-Schicht.
  Konstruktorinjektion; Schreibtransaktionen liegen im Adapter, Lesezugriffe sind
  standardmäßig read-only. `open-in-view=false` beibehalten.
- API-Records `ExpenseRequest` und `Expense` sind von `ExpenseEntity` getrennt.
  Geldbeträge: `BigDecimal` / PostgreSQL `NUMERIC` ohne feste Skala, Datum: `LocalDate`.
  IDs erzeugt die Datenbank; Lücken sind zulässig. Listen sind nach ID aufsteigend sortiert.
- Bean Validation verlangt eine nichtleere Beschreibung, einen positiven Betrag
  und ein Datum. Fehler werden über `ApiExceptionHandler` als Problem Details
  (`application/problem+json`) ausgegeben; Feldfehler enthalten `errors`.
- REST-Vertrag: POST `/api/expenses` → 201 mit Location; GET Liste/Eintrag → 200;
  PUT vorhandener Eintrag → 200; DELETE → 204. Unbekannte Einzel-IDs → 404,
  ungültige Eingaben → 400. PUT legt keine fehlenden Einträge an.
  GET `/api/health` liefert `{"status":"UP"}` und prüft die Datenbank nicht explizit.
- Liquibase ist allein für Schemaänderungen zuständig, Hibernate verwendet
  `ddl-auto=validate`. Master: `src/main/resources/db/changelog/db.changelog-master.yaml`;
  Migrationen sind eingebundene Liquibase-formattierte SQL-Dateien. Kein Flyway.

## Vorhandene Befehle und Testabläufe

Im Projektverzeichnis ausführen; OrbStack muss für Datenbankbetrieb und Tests laufen.
Beim ersten Lauf werden Gradle, Abhängigkeiten und Container-Images heruntergeladen.

| Zweck | Befehl |
| --- | --- |
| Tests mit automatisch gestarteter PostgreSQL-Datenbank | `./gradlew test` |
| Einzelne API-Testklasse | `./gradlew test --tests de.example.expensetracker.ExpenseApiTest` |
| Build einschließlich Tests | `./gradlew build` |
| Ausführbare JAR erstellen | `./gradlew bootJar` |
| Compose-Konfiguration prüfen | `docker compose config --quiet` |
| Lokale Datenbank starten und Bereitschaft abwarten | `docker compose up -d --wait` |
| Anwendung starten (Port 8080) | `./gradlew bootRun` |
| JAR starten | `java -jar build/libs/expense-tracker-0.0.1-SNAPSHOT.jar` |
| Health prüfen | `curl -i http://localhost:8080/api/health` |
| Lokale Datenbank stoppen, Volume behalten | `docker compose down` |

- Tests: JUnit Jupiter, `@SpringBootTest`, MockMvc und gemeinsame
  `PostgresTestConfiguration` mit `@ServiceConnection`. PostgreSQL 17.11 wird
  automatisch über Testcontainers gestartet; keine manuelle Testdatenbank, kein H2.
  Liquibase läuft auch in Tests. Testbericht: `build/reports/tests/test/index.html`.
- API-Tests leeren `expenses` vor jedem Test per JdbcTemplate. IDs aus Antworten
  verwenden; der Identity-Zähler wird nicht zurückgesetzt. Tests teilen die
  Datenbank und sind aktuell auf sequentielle Ausführung ausgelegt.
- Compose verwendet ebenfalls PostgreSQL 17.11, Port 5432 auf localhost und das
  benannte Volume `postgres_data`. Tests verwenden dieses Volume nicht.
  Anwendung: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; Compose zusätzlich `DB_PORT`.
  Bei geändertem Host-Port auch `DB_URL` anpassen. Lokale Standardwerte siehe README.
- Persistenznachweis ist manuell in der README beschrieben: Ausgabe erstellen,
  Anwendung stoppen und neu starten, dieselbe Ausgabe abrufen, weitere ID prüfen.
  Es gibt keinen separaten `persistenceTest`-Task.

## Neu vorgeschlagene Entwicklungsregeln

- Bestehende Paketstruktur und API-Verträge fortführen. Neue Schichten oder
  Abhängigkeiten nur für einen konkreten Bedarf einführen; Entities nicht als
  REST-Antworten exponieren. Keine automatische Rundung auf zwei Nachkommastellen.
- Bereits angewendete Liquibase-Changesets nicht nachträglich ändern. Jede
  Schemaänderung als neue nummerierte SQL-Datei mit eindeutiger Changeset-ID
  ergänzen und im Master einbinden. Entity und Migration zusammen aktualisieren;
  weder Flyway noch Hibernate `update`/`create` als Ersatz einführen.
- Bei Verhaltensänderungen aussagekräftige Tests ergänzen und `./gradlew test`
  ausführen. CRUD, Validierung, Problem Details, Dezimalpräzision und Sortierung
  abdecken. Bei Schemaänderungen Migration gegen frisches PostgreSQL prüfen;
  bei Persistenzänderungen zusätzlich den manuellen Neustarttest durchführen.
  Docker-Probleme diagnostizieren, Tests nicht stillschweigend überspringen.
- Compose- und Testcontainers-Image bei Versionsänderungen gemeinsam aktualisieren.
  Keine produktiven Zugangsdaten ins Repository aufnehmen. `docker compose down -v`
  oder andere Löschungen persistenter Daten nur auf ausdrücklichen Auftrag ausführen.
- Vor Änderungen `git status` prüfen, fremde Änderungen erhalten und den Diff auf
  die Aufgabe begrenzen. Keine Commits, Pushes oder destruktiven Git-Befehle ohne
  ausdrücklichen Auftrag. Vor Abschluss `git diff --check` ausführen und tatsächliche
  Testergebnisse sowie nicht durchgeführte Prüfungen nennen.
- README bei Änderungen an Start, Konfiguration oder API mitpflegen. Vorhandene
  Werkzeuge nutzen; zusätzliche Werkzeuge nur auf ausdrücklichen Auftrag installieren.
