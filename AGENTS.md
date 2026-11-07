# Projektleitfaden

## Dokumentation und Quellen

- [README](README.md): Einrichtung, Konfiguration, API-Beispiele und manuelle Abnahme.
  Startanleitungen dort pflegen, statt sie hier oder unter `docs/` zu duplizieren.
- [Architekturentscheidungen](docs/architecture-decisions.md): Begründungen,
  Abwägungen und Grenzen der bestehenden Architektur. Bei Änderungen an
  Authentifizierung, Besitzerzuordnung oder Persistenz die betroffene Entscheidung
  mitpflegen; geplante Fähigkeiten nicht als implementiert darstellen.
- Konkrete Abhängigkeits- und Image-Versionen aus `build.gradle.kts`, `compose.yaml`
  und `PostgresTestConfiguration` ablesen. Lokale Nutzer, Ausgaben-IDs, Passwörter,
  Session-Cookies und Inhalte aus `build/` sind keine dauerhafte Projektdokumentation.

## Bestehende Projektkonventionen

- Java 21, Spring Boot 3.5.x (aktuell 3.5.16), Gradle Wrapper mit Kotlin DSL.
  Vorhandenes JDK verwenden; automatischer JDK-Download ist deaktiviert.
  Abhängigkeitsversionen verwaltet das Spring-Boot-BOM.
- Basispaket: `de.example.expensetracker`, nach Fachlichkeit gegliedert
  (`expense`, `health`, Authentifizierung in `security`, zentrale MVC-Fehlerbehandlung
  in `error`). Security-Filter geben ihre Problem Details in `SecurityConfiguration`
  aus; sie laufen vor der MVC-Fehlerbehandlung.
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
  PUT vorhandener Eintrag → 200; DELETE → 204. Unbekannte und fremde Einzel-IDs → 404,
  ungültige Eingaben → 400. PUT legt keine fehlenden Einträge an. Datumsfilter
  `from`/`to` sind optional und inklusive; `from > to` liefert 400.
  GET `/api/health` liefert `{"status":"UP"}` und prüft die Datenbank nicht explizit.
- Liquibase ist allein für Schemaänderungen zuständig, Hibernate verwendet
  `ddl-auto=validate`. Master: `src/main/resources/db/changelog/db.changelog-master.yaml`;
  Migrationen sind eingebundene Liquibase-formattierte SQL-Dateien. Kein Flyway.

## Authentifizierung und Besitzertrennung

- Spring Security als OIDC-Client mit Authorization Code Flow und serverseitiger
  Session fortführen. Genau eine Registrierung `keycloak` für den Realm
  `expense-tracker` und Client `expense-tracker-web`; keine eigene Passwortablage,
  lokale Benutzertabelle oder zusätzliche Basic-/Bearer-Authentifizierung einführen,
  ohne die Architekturentscheidung ausdrücklich zu ändern.
- Fachliche Endpoints standardmäßig schützen. Öffentliche Ausnahmen auf Health,
  Login-/OIDC-Ablauf, Abmeldebestätigung und Fehlerroute begrenzen. API-Aufrufe ohne
  Session liefern 401 als Problem Details statt eines Login-Redirects; angemeldete
  Aufrufe ohne gültigen CSRF-Schutz liefern 403. OIDC-Callback-Fehler dürfen keine
  erneute Login-Schleife auslösen.
- CSRF für Schreibzugriffe einschließlich `POST /logout` beibehalten.
  `/api/me` und `/api/csrf` benötigen eine Anmeldung. Logout muss sowohl die lokale
  Session beenden als auch den Browser zum OIDC-Logout bei Keycloak weiterleiten.
- Die Benutzer-ID zentral über `CurrentUser` aus dem OIDC-Claim `sub` beziehen.
  Benutzername, E-Mail und vom Client übergebene Besitzerangaben sind keine
  Autorisierungsgrundlage. `owner_subject` bleibt außerhalb der API-Records.
- Alle fachlichen Adapterzugriffe müssen eine Besitzer-ID verlangen; bereits die
  Datenbankabfrage muss nach Besitzer filtern. Das gilt für Listen, jede Variante
  der Datumsfilter, Einzelzugriffe sowie gesperrte PUT-/DELETE-Abfragen. Keine
  ungefilterten Zugriffe oder nachträgliche Filterung geladener Gesamtlisten ergänzen.
- Bestehende Zeilen mit `owner_subject IS NULL` bleiben gespeichert und für alle
  Nutzer unsichtbar. Neue Ausgaben bekommen zwingend den angemeldeten Besitzer;
  Updates ändern ihn nicht. Altdatenzuordnung und Realm-Wechsel erfordern eine
  ausdrückliche Datenmigration; nie automatisch anhand von Namen oder E-Mail zuordnen.
- PUT/DELETE behalten `PESSIMISTIC_WRITE` innerhalb der Schreibtransaktion.
  Bei Nebenläufigkeitsänderungen sowohl Besitzerfilter als auch das 404-Verhalten
  nach einer während der Wartezeit bestätigten Löschung erhalten.

## Vorhandene Befehle und Testabläufe

Im Projektverzeichnis ausführen; OrbStack muss für Datenbankbetrieb und Tests laufen.
Beim ersten Lauf werden Gradle, Abhängigkeiten und Container-Images heruntergeladen.
Für Start, Stop, JAR-Ausführung und Umgebungsvariablen die README verwenden.

| Zweck | Befehl |
| --- | --- |
| Tests mit automatisch gestarteter PostgreSQL-Datenbank | `./gradlew test` |
| Einzelne API-Testklasse | `./gradlew test --tests de.example.expensetracker.ExpenseApiTest` |
| Authentifizierung und Besitzertrennung | `./gradlew test --tests de.example.expensetracker.SecurityApiTest` |
| Migration vorhandener Ausgaben | `./gradlew test --tests de.example.expensetracker.ExpenseMigrationTest` |
| Build einschließlich Tests | `./gradlew build` |
| Compose-Konfiguration prüfen | `docker compose config --quiet` |

- Tests: JUnit Jupiter, `@SpringBootTest`, MockMvc und gemeinsame
  `PostgresTestConfiguration` mit `@ServiceConnection`. PostgreSQL 17.11 wird
  automatisch über Testcontainers gestartet; keine manuelle Testdatenbank, kein H2.
  Liquibase läuft auch in Tests. Testbericht: `build/reports/tests/test/index.html`.
- `PostgresTestConfiguration` stellt zusätzlich eine lokale OIDC-Clientregistrierung
  ohne Discovery bereit. Für `./gradlew test` sind weder laufendes Keycloak noch
  lokale Client-Secrets nötig. Fachliche MockMvc-Tests nutzen `oidcLogin()` und
  für Schreibzugriffe `csrf()`; die Security-Filter nicht abschalten.
- Security-Tests müssen getrennte Identitäten, fremde und besitzerlose Datensätze,
  alle Datumsfilter und direkte GET-/PUT-/DELETE-Zugriffe abdecken. Zusätzlich
  Besitzer-Manipulation, anonyme Aufrufe, fehlende/ungültige CSRF-Token, lokale
  Session-Invalidierung und OIDC-Fehlerantworten prüfen.
- API-Tests leeren `expenses` vor jedem Test per JdbcTemplate. IDs aus Antworten
  verwenden; der Identity-Zähler wird nicht zurückgesetzt. Tests teilen die
  Datenbank und sind aktuell auf sequentielle Ausführung ausgelegt.
- MockMvc prüft keinen echten Token-Austausch und keinen abgeschlossenen Logout
  bei Keycloak. Nach Änderungen an OIDC, Realm/Client, Logout oder deren Konfiguration
  zusätzlich die README-Abnahme mit zwei Nutzern gegen echtes Keycloak durchführen.
  Dabei Login, Passwortänderung, Zugriffstrennung und vollständigen Logout prüfen;
  bei Persistenzänderungen zusätzlich die betroffenen Neustarts. Es gibt keinen
  separaten `persistenceTest`-Task und keinen eingecheckten echten Keycloak-End-to-End-Test.

## Lokale Infrastruktur und Konfiguration

- Keycloak und seine PostgreSQL-Datenbank laufen in Compose; der Tracker läuft auf
  dem Host. `postgres_data` enthält Ausgaben, `keycloak_data` die Keycloak-Daten.
  Beide Volumes erhalten; Testcontainers verwendet keines davon. App-Nutzer im
  Realm `expense-tracker` anlegen, `master` bleibt der Administration vorbehalten.
- Compose liest `.env` automatisch, die Java-Anwendung nicht. Vor `bootRun` oder
  JAR-Start die Variablen gemäß README in dasselbe Shell-Environment exportieren.
  Bei `invalid_client_credentials` das App-Environment und das tatsächlich in
  Keycloak gespeicherte Client-Secret prüfen; Secret-Werte nicht ausgeben.
- Realm-Import ist eine Initialisierung, kein Konfigurationsabgleich. Änderungen
  an der Importdatei oder `.env` aktualisieren bestehende Realms, Client-Secrets
  und Datenbankpasswörter nicht automatisch. Änderungen an bestehenden Installationen
  gesondert dokumentieren; Volumes nicht zum Erzwingen eines Imports löschen.
- Issuer, Callback, Logout-Rückkehradresse und Keycloak-Hostname konsistent halten.
  Das lokale Setup verwendet `localhost`; nicht beiläufig durch `127.0.0.1` ersetzen.
  Keycloak vor der Anwendung bereitstellen, da diese beim Start Discovery nutzt.

## Entwicklungsregeln

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
- PostgreSQL-Images beider Compose-Datenbanken und das Testcontainers-Image bei
  Versionsänderungen gemeinsam aktualisieren; Keycloak separat fest versionieren.
  Keine echten Zugangsdaten, Tokens oder Session-Cookies ins Repository aufnehmen;
  `.env.example` enthält nur Platzhalter. `docker compose down -v`
  oder andere Löschungen persistenter Daten nur auf ausdrücklichen Auftrag ausführen.
- Vor Änderungen `git status` prüfen, fremde Änderungen erhalten und den Diff auf
  die Aufgabe begrenzen. Keine Commits, Pushes oder destruktiven Git-Befehle ohne
  ausdrücklichen Auftrag. Vor Abschluss `git diff --check` ausführen und tatsächliche
  Testergebnisse sowie nicht durchgeführte Prüfungen nennen.
- README bei Änderungen an Start, Konfiguration oder API mitpflegen. Vorhandene
  Werkzeuge nutzen; zusätzliche Werkzeuge nur auf ausdrücklichen Auftrag installieren.
