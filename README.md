# Expense Tracker

REST-API mit Java 21, Spring Boot 3.5.16, Gradle 8.14.3 und PostgreSQL 17.11.
Keycloak 26.8.2 übernimmt die Benutzerverwaltung. Anmeldung erfolgt im Browser über
OpenID Connect; der Tracker verwendet eine serverseitige Session. Jeder Benutzer
sieht und bearbeitet ausschließlich seine eigenen Buchungen. Eine deutsche
React-Oberfläche zeigt Einnahmen und Ausgaben in EUR und ermöglicht deren
Erfassung, Bearbeitung und Löschung.

Entwicklungsregeln stehen in [AGENTS.md](AGENTS.md), Begründungen und Grenzen der
Architektur in den [Architekturentscheidungen](docs/architecture-decisions.md).

## Voraussetzungen und lokaler Start

- Vorhandenes JDK 21 (`java -version`, gegebenenfalls `JAVA_HOME`). Kein automatischer JDK-Download.
- Node gemäß `frontend/.node-version` und pnpm gemäß `frontend/package.json` müssen
  bereits installiert und im PATH verfügbar sein. Gradle installiert keine Werkzeuge.
- OrbStack muss laufen. Gradle Wrapper ist enthalten; keine zusätzliche Installation nötig.
- Beim ersten Lauf Internetzugang für Abhängigkeiten und Container-Images.

```sh
cp .env.example .env
# In .env drei eigene, unterschiedliche lokale Passwörter/Secrets setzen.
# .env ist von Git ausgeschlossen. Beispielwerte vor dem Start ersetzen.
set -a
source .env
set +a

docker compose config --quiet
docker compose up -d --wait
./gradlew bootRun
```

Falls bereits eine `.env` existiert, diese behalten und nur fehlende Variablen ergänzen.
Compose liest `.env` automatisch. `bootRun` und `java -jar` benötigen die Variablen
im Shell-Environment, daher auch in einem neuen Terminal zuerst `.env` laden.
Keycloak muss vor dem Tracker bereit sein: Der Tracker liest beim Start die
OIDC-Konfiguration des Realms und prüft die Identität gegen dessen Issuer.

| Dienst | Adresse / Konfiguration |
| --- | --- |
| Expense Tracker | http://localhost:8080 |
| Keycloak Admin-Konsole | http://localhost:8081/admin/ |
| Keycloak Kontoverwaltung | http://localhost:8081/realms/expense-tracker/account/ |
| Tracker-Datenbank | localhost:5432, DB/Benutzer/Passwort lokal jeweils `expensetracker` |
| Keycloak-Datenbank | Nur im Docker-Netzwerk; eigene DB und eigenes Volume |

`KEYCLOAK_ADMIN_USERNAME` (Standard `admin`) und `KEYCLOAK_ADMIN_PASSWORD` erstellen
beim ersten Start den Bootstrap-Administrator. `KEYCLOAK_DB_PASSWORD` ist das
Passwort der separaten Keycloak-Datenbank. `KEYCLOAK_CLIENT_SECRET` muss in Keycloak
und der Anwendung übereinstimmen. Keine produktiven Zugangsdaten verwenden.

Der Realm `expense-tracker` und der vertrauliche Client `expense-tracker-web`
werden beim ersten Start aus `keycloak/expense-tracker-realm.json` importiert.
Bereits vorhandene Realms werden beim Neustart **nicht überschrieben**. Änderungen
an der Importdatei oder am Client-Secret in `.env` aktualisieren einen bestehenden
Realm nicht: Änderungen auch in der Admin-Konsole vornehmen, insbesondere unter
Clients → expense-tracker-web → Credentials. Bestehende Volumes nicht löschen.
Auch Datenbankpasswörter ändern sich durch Änderung der Umgebungsvariable nicht
nachträglich in bereits initialisierten PostgreSQL-Volumes.

## Nutzer anlegen und Passwörter verwalten

1. In der Admin-Konsole mit den Zugangsdaten aus `.env` anmelden.
2. Zum Realm **expense-tracker** wechseln; App-Benutzer nicht im Realm `master` anlegen.
3. Unter Users → Add user einen Benutzer mit Benutzername und Profil anlegen.
4. Unter Credentials ein Passwort setzen und **Temporary** einschalten.
5. Der Benutzer öffnet http://localhost:8080/login und vergibt beim ersten Login sein eigenes Passwort.
6. Spätere Passwortänderungen sind über die Keycloak-Kontoverwaltung möglich.

Selbstregistrierung, Direct Access Grants und „Passwort vergessen“ sind im
importierten Realm deaktiviert. Für einen späteren E-Mail-Passwortreset müssten
SMTP und die entsprechende Realm-Option eingerichtet werden. Alle Benutzer dieses
Realms dürfen die App nutzen; eine zusätzliche App-Rollenvergabe ist nicht nötig.
Deaktivieren/Löschen eines Kontos in Keycloak beendet eine bereits bestehende
Tracker-Session nicht unmittelbar. Der Tracker verwendet einen Session-Timeout
von 30 Minuten Inaktivität; eine zentrale sofortige Session-Sperrung ist nicht enthalten.

### Test User

expense-test-alice
Test-Alice-a62c32f99620!
expense-test-bob
Test-Bob-9cf5b68aa76c!

## Login, Session und Logout

| Endpoint | Verhalten |
| --- | --- |
| `GET /login` | Startet Browserlogin bei Keycloak; danach Weiterleitung auf die Übersicht `/` |
| `GET /api/me` | `{"id":"<sub>","username":"<preferred_username>"}` |
| `GET /api/csrf` | `{"token":"…","headerName":"X-CSRF-TOKEN"}` für die aktuelle Session |
| `POST /logout` | Mit CSRF-Token: lokale Session beenden, Weiterleitung zum Keycloak-Logout |
| `GET /logged-out` | Öffentliche Abmeldebestätigung mit Link zur erneuten Anmeldung |
| `GET /api/health` | Öffentlich: `{"status":"UP"}`; keine explizite Datenbankprüfung |

Nicht angemeldete API-Aufrufe liefern `401` mit `application/problem+json`.
Angemeldete Schreibzugriffe ohne gültiges CSRF-Token liefern `403` im gleichen
Format. Fehlgeschlagene OIDC-Callbacks liefern ebenfalls `401`: für Browser mit HTML-Accept
eine Fehlerseite mit Login-Link, sonst Problem Details;
über `/login` lässt sich die Anmeldung erneut starten. Die API verwendet Session-Cookies, keine Basic-Authentifizierung oder
Bearer-Tokens. Ein erfolgreicher Keycloak-Login allein authentifiziert noch keine
beliebigen curl-Aufrufe: Diese müssten die Tracker-Session mitsenden.

Nach dem Login können Aufrufe direkt in der Browserkonsole auf **localhost:8080**
ausgeführt werden. Cookies werden bei diesen Aufrufen automatisch mitgesendet:

```javascript
const csrf = await fetch('/api/csrf').then(r => r.json());
const headers = {'Content-Type': 'application/json', [csrf.headerName]: csrf.token};

// Erstellen: 201 mit Location-Header
const created = await fetch('/api/expenses', {
  method: 'POST', headers,
  body: JSON.stringify({description: 'Mittagessen', amount: 12.34, date: '2026-11-03'})
}).then(r => r.json());

// Liste und Eintrag: 200
await fetch('/api/expenses').then(r => r.json());
await fetch(`/api/expenses/${created.id}`).then(r => r.json());

// Datumsfilter (inklusive Grenzen, auch einzeln verwendbar)
await fetch('/api/expenses?from=2026-11-01&to=2026-11-30').then(r => r.json());

// Vollständig aktualisieren: 200; PUT erstellt keine fehlenden Einträge
await fetch(`/api/expenses/${created.id}`, {
  method: 'PUT', headers,
  body: JSON.stringify({description: 'Abendessen', amount: 25.75, date: '2026-11-04'})
}).then(r => r.json());

// Löschen: 204
await fetch(`/api/expenses/${created.id}`, {method: 'DELETE', headers});
```

Zum Logout muss der Browser der Weiterleitung zu Keycloak als Seitennavigation
folgen. Ein `fetch('/logout')` ist wegen der Weiterleitung auf den anderen Port
ungeeignet. Stattdessen ein Formular absenden:

```javascript
const logoutCsrf = await fetch('/api/csrf').then(r => r.json());
const form = document.createElement('form');
form.method = 'POST';
form.action = '/logout';
const input = document.createElement('input');
input.type = 'hidden';
input.name = '_csrf';
input.value = logoutCsrf.token;
form.append(input);
document.body.append(form);
form.submit();
```

Nach Logout und Rückkehr auf `/logged-out` führt `/login` erneut zur Anmeldung.
Ein anschließender Login benötigt ein neues CSRF-Token. Sessions werden im
Anwendungsspeicher gehalten; nach einem Tracker-Neustart erneut `/login` öffnen.
Eine noch aktive Keycloak-Session kann dabei einen erneuten Passwortdialog ersparen.

## Web-Oberfläche und Frontend-Entwicklung

Nach dem Start http://localhost:8080 öffnen. Die Anmeldung erfolgt bei Keycloak;
danach erscheint die Monatsübersicht. Monatswechsel und ein freier Zeitraum steuern
Liste und Summen. Die Differenz ist Einnahmen minus Ausgaben im gewählten Zeitraum,
kein Kontostand. Die Oberfläche sortiert nach Datum und ID absteigend. Desktop zeigt
Spalten, kleine Displays zeigen Buchungskarten. Kategorien, Konten, Diagramme und
Währungsumrechnung sind nicht enthalten.

`./gradlew bootRun`, `build` und `bootJar` bauen das Frontend über vorhandenes pnpm
und übernehmen `frontend/dist` in das JAR. Auch `test` benötigt den Frontend-Build
für die Routing-Tests. Projektabhängigkeiten installiert Gradle mit dem Lockfile;
Node und pnpm werden nicht automatisch installiert. Liegt pnpm außerhalb des PATH,
kann `-PpnpmExecutable=/absoluter/pfad/zu/pnpm` verwendet werden.

Für Entwicklung mit Hot Reload zunächst im ersten Terminal:

```sh
cd frontend
pnpm install --frozen-lockfile
pnpm dev
```

Im zweiten Terminal im Projektverzeichnis `.env` wie oben laden, Compose starten
und anschließend:

```sh
./gradlew bootRun -PfrontendDev=true
```

Den Browser weiterhin auf **http://localhost:8080** öffnen. Das Profil `frontend-dev`
bezieht HTML und Module vom lokalen Vite-Server auf Port 5173; Spring liefert die
Module unter `/__vite/` aus. Nur der Hot-Reload-WebSocket verbindet sich direkt mit
5173. Session, Fach-API, Login und Logout bleiben auf 8080. Das Profil ist nur für
lokale Entwicklung vorgesehen; es gibt keinen Vite-Proxy im Produktionsprofil.
Ein zusätzlich gesetztes `SPRING_PROFILES_ACTIVE` ist für diesen Ablauf nicht nötig.

Das Frontend lädt `/api/me`, beschafft CSRF-Token vor Schreibzugriffen und verwendet
beim Logout ein Formular. Bei abgelaufener Session wird der Nutzercache geleert und
eine erneute Anmeldung angeboten. Nicht gespeicherte Formulare gehen dabei verloren;
Schreibzugriffe werden nach Fehlern nicht automatisch wiederholt. Feld- und
Netzwerkfehler beim Speichern erhalten die Eingaben in der laufenden Sitzung.

## Buchungs-API

`/api/bookings` verwaltet beide Arten auf derselben Datengrundlage. CRUD-Statuscodes,
Datumsfilter, Besitzertrennung und Problem Details entsprechen der Ausgaben-API.
Listen sind nach ID aufsteigend sortiert. Ein Beispiel für POST oder PUT:

```json
{"type":"INCOME","description":"Honorar","amount":"123.456789","date":"2026-11-03"}
```

Antworten enthalten zusätzlich die Datenbank-`id`. `type` ist `INCOME` oder
`EXPENSE`; der Betrag ist für beide Arten positiv. **Die neue API verlangt und
liefert Dezimalstrings**, mit Punkt als Dezimaltrenner, ohne Vorzeichen oder
Exponentialschreibweise. JSON-Zahlen werden hier abgelehnt. Die Oberfläche nimmt
Komma oder Punkt entgegen, rechnet mit Dezimalarithmetik und zeigt mindestens zwei,
bei Bedarf alle weiteren Nachkommastellen. Sie rundet gespeicherte Beträge nicht.

Die Übersicht lädt sämtliche Buchungen des Zeitraums und berechnet ihre Summen im
Client. Es gibt zunächst keine Pagination und keinen Summen-Endpunkt. EUR ist eine
gemeinsame Darstellungsannahme, kein Währungsfeld pro Datensatz.

Die bisherige `/api/expenses`-API bleibt erhalten: Beträge sind dort JSON-Zahlen,
alle Zugriffe betreffen ausschließlich Ausgaben. Einnahmen-IDs liefern dort `404`.
Über `/api/bookings` lässt sich die Art einer eigenen Buchung ändern; danach ist sie
entsprechend über die alte API sichtbar oder unsichtbar. Bereits vorhandene Zeilen
werden durch ein neues Liquibase-Changeset als `EXPENSE` übernommen. Besitzerlose
Altdaten bleiben unsichtbar. Die Tabellennamensgebung `expenses` bleibt erhalten.

## Ausgabenvertrag und Datenhaltung

Ausgaben enthalten `id`, `description`, `amount`, `date`. Die Datenbank vergibt IDs;
Lücken sind zulässig. Die Beschreibung muss nichtleer, der Betrag positiv und das
Datum im Format `YYYY-MM-DD` vorhanden sein. Beträge bleiben `BigDecimal`/`NUMERIC`
ohne feste Skala und ohne automatische Rundung. Gemeinsame Darstellungswährung ist EUR.
Listen sind nach ID aufsteigend sortiert; ohne Treffer `[]`.

`from` und `to` filtern das Ausgabedatum inklusive Grenzen. Ungültige Datumswerte
oder `from > to` liefern `400`. Unbekannte und fremde Einzel-IDs liefern für GET,
PUT und DELETE gleichermaßen `404`. Erfolgreiche Aufrufe behalten den CRUD-Vertrag:
POST `201` mit Location, GET/PUT `200`, DELETE `204`.

Fehler liefern Problem Details mit `type`, `title`, `status`, `detail`, `instance`.
Feldvalidierungsfehler enthalten zusätzlich `errors` mit Feld und Meldung. Ungültige
Pfad-IDs und unlesbares JSON liefern `400`.

Der Besitzer wird ausschließlich aus dem OIDC-Claim `sub` der Session bestimmt.
`owner_subject` ist kein Teil der API-Records; zusätzliche Besitzerangaben im JSON
ändern die Zuordnung nicht. Alle fachlichen Datenbankabfragen filtern nach diesem
Besitzer, einschließlich der Schreibsperren für PUT/DELETE. Benutzername und E-Mail
können sich ändern, ohne die Zuordnung zu beeinflussen. Ein neu angelegter Benutzer
mit gleichem Namen erhält eine neue ID und keinen Zugriff auf die alten Ausgaben.

Bereits vorhandene Ausgaben bleiben nach Migration mit `owner_subject = NULL`
unverändert gespeichert und sind für alle Nutzer unsichtbar. Eine spätere Zuordnung
ist eine separate, bewusste Datenmigration. Keine automatische Übernahme beim Login.
Bei Wechsel des konfigurierten Realms müssen Besitzer-IDs ebenfalls ausdrücklich
migriert werden; die Anwendung ist für genau einen Realm vorgesehen.

Architektur: Controller → Repository-Adapter → Spring Data JPA → PostgreSQL.
Keine zusätzliche Service-Schicht. Liquibase ist allein für Schemaänderungen
zuständig; Hibernate validiert das Schema, `open-in-view=false` bleibt erhalten.
PUT/DELETE verwenden eine pessimistische Schreibsperre; nach einer konkurrierenden
bestätigten Löschung erhält ein wartender Schreibzugriff `404`. Eine Versionsprüfung
für veraltete Clientdaten ist damit nicht verbunden.

## Tests und Build

```sh
./gradlew test
./gradlew test --tests de.example.expensetracker.SecurityApiTest
./gradlew test --tests de.example.expensetracker.ExpenseApiTest
./gradlew build
./gradlew bootJar
# .env vorher ins Environment laden, Keycloak und PostgreSQL starten
java -jar build/libs/expense-tracker-0.0.1-SNAPSHOT.jar
```

JUnit Jupiter, MockMvc und Testcontainers starten PostgreSQL 17.11 automatisch.
Keine manuelle Testdatenbank, kein H2. Liquibase läuft auch in Tests. Die Tests
verwenden eine lokale Test-Clientregistrierung und gemockte OIDC-Identitäten;
Keycloak muss für diese Tests nicht laufen und Client-Secrets sind nicht nötig.
Sie prüfen CRUD, Validierung, Problem Details, Dezimalpräzision, Datumsfilter,
Sortierung, konkurrierende Löschungen, Besitzertrennung, CSRF, Logout und Migration
bestehender Daten. Tests teilen eine Datenbank und laufen sequenziell.
Bericht: `build/reports/tests/test/index.html`. Docker-Probleme führen zum Testfehler.

Frontend-Prüfungen im Verzeichnis `frontend`:

```sh
pnpm test
pnpm build
# Chromium einmalig ausdrücklich als Testbrowser bereitstellen:
pnpm exec playwright install chromium
pnpm test:e2e
```

`./gradlew build` führt zusätzlich die Vitest-Komponententests aus. Playwright baut
die Oberfläche und startet selbst einen Preview-Server auf Port 4173. Die Tests
prüfen Erstellen, Bearbeiten, Löschen und Darstellung auf Desktop und Smartphone
mit gemockter API und Session. Diese Tests belegen keinen echten OIDC-Login.
`FrontendRoutingTest` prüft die Spring-Auslieferung und die Trennung von API-/UI-Routen.
`BookingApiTest` ergänzt CRUD, Einnahmen, Dezimalstrings, Besitzertrennung,
CSRF und konkurrierende Löschungen. Browserberichte stehen unter
`frontend/test-results` und sind keine dauerhafte Projektdokumentation.

## Manueller Abnahme- und Persistenztest

1. Compose und Tracker wie oben starten. Zwei Benutzer A und B anlegen.
2. Auf http://localhost:8080 mit A einloggen und das temporäre Passwort ersetzen.
   In der Oberfläche eine Einnahme und eine Ausgabe erstellen, Zeitraum und Summen
   prüfen, eine Buchung bearbeiten und eine weitere nach Bestätigung löschen.
   Bei schmalem Browserfenster Tastaturbedienung und Kartenansicht prüfen.
   Eine Ausgabe über die Browserkonsole erstellen; ID und Antwort notieren. Kontoverwaltung öffnen und
   Passwort erneut ändern, danach den Login mit dem neuen Passwort prüfen.
3. Mit einem getrennten Browserprofil als B anmelden. Eigene Ausgabe erstellen.
   Listen und alle Datumsfilter dürfen nur Bs Ausgabe zeigen. Direkte GET-, PUT-
   und DELETE-Aufrufe auf As ID müssen `404` liefern. Als A entsprechend Bs ID prüfen.
4. Als angemeldeter Nutzer einen Schreibaufruf ohne CSRF-Header senden: `403`.
   Ohne Session `/api/expenses` aufrufen: `401`; `/api/health` bleibt `200`.
5. Tracker mit Ctrl+C stoppen und erneut starten. Als A anmelden, notierte Ausgabe
   abrufen und Inhalt vergleichen. Weitere Ausgabe erstellen: neue ID.
6. `docker compose restart keycloak-db keycloak` und danach
   `docker compose up -d --wait keycloak` ausführen. Benutzer und deren geänderte
   Passwörter müssen weiter funktionieren; Ausgaben bleiben erhalten.
7. Mit dem Logout-Formular abmelden. Erwartet: `/logged-out`, API anschließend `401`,
   erneutes `/login` zeigt wieder die Keycloak-Anmeldung.

`docker compose down` behält beide benannten Volumes `postgres_data` und
`keycloak_data`. **Kein `docker compose down -v` ausführen**, wenn die Daten erhalten
bleiben sollen. Testcontainers verwendet diese Volumes nicht.

## Weitere Konfiguration

- Tracker-Datenbank: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- Compose-Hostport für Tracker-PostgreSQL: `DB_PORT` (Standard 5432); bei Änderung
  auch `DB_URL` anpassen. Keycloak-PostgreSQL veröffentlicht keinen Hostport.
- OIDC-Issuer: `KEYCLOAK_ISSUER_URI` (Standard `http://localhost:8081/realms/expense-tracker`).
- Callback: `http://localhost:8080/login/oauth2/code/keycloak`;
  Logout-Rückkehr: `http://localhost:8080/logged-out`. Bei Adressänderungen auch den
  Client in Keycloak anpassen. Für Login konsistent `localhost`, nicht `127.0.0.1`, verwenden.

Dieses Setup ist für lokale Entwicklung mit HTTP und Keycloak `start-dev` gedacht.
Öffentlicher Betrieb mit HTTPS, sicheren Cookies, Backups und Betriebsabsicherung
ist ein eigener Schritt. Der Tracker wird weiterhin außerhalb von Docker gestartet.
