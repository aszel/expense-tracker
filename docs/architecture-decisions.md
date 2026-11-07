# Architekturentscheidungen

Diese Aufzeichnung beschreibt die im Repository umgesetzte Architektur, mit
Schwerpunkt auf der Keycloak-Anbindung. Alle folgenden Entscheidungen gelten im
aktuellen Stand. Die Begründungen erläutern die gewählten Abwägungen;
weitergehende Fähigkeiten in den Abschnitten „Grenzen“ sind nicht implementiert.

Die [README](../README.md) bleibt die Anleitung für Betrieb, Konfiguration und
API-Nutzung. [AGENTS.md](../AGENTS.md) enthält die verbindlichen Entwicklungsregeln.
Hier werden diese Abläufe und Regeln nicht als weitere Referenzlisten wiederholt.

## 1. Schlanker Datenfluss mit getrennten API- und Persistenzmodellen

**Kontext:** Die Anwendung verwaltet Einnahmen und Ausgaben als REST-Ressourcen. Die aktuelle
Fachlogik erfordert keine zusätzliche Orchestrierungsschicht.

**Entscheidung:** Der Controller ruft einen Repository-Adapter auf, der API-Records
und JPA-Entity abbildet und über Spring Data JPA auf PostgreSQL zugreift. Der Adapter
verwaltet die Transaktionen. Die Identität wird am HTTP-Zugriff über `CurrentUser`
ermittelt und als Besitzer-ID an den Adapter übergeben.

**Begründung und Folgen:** Eine zusätzliche Service-Schicht würde derzeit vor allem
Aufrufe weiterreichen. Die Trennung der Modelle ermöglicht dagegen, interne Felder
wie den Besitzer hinzuzufügen, ohne das REST-Format zu erweitern. Der Adapter kennt
keine HTTP-Session und lässt sich mit expliziten Besitzer-IDs aufrufen. Eine spätere
Service-Schicht braucht einen konkreten fachlichen Anlass.

**Nachweis:** [ExpenseController](../src/main/java/de/example/expensetracker/expense/ExpenseController.java),
[ExpenseRepository](../src/main/java/de/example/expensetracker/expense/ExpenseRepository.java).

## 2. Keycloak für Identitäten, OIDC-Client und lokale Session im Tracker

**Kontext:** Nutzer sollen sich im Browser anmelden, Passwörter selbst verwalten
und sich wieder abmelden können. Die React-Oberfläche nutzt diese Anmeldung und die serverseitige Session.

**Entscheidung:** Keycloak übernimmt Nutzerkonten und Zugangsdaten. Der Tracker ist
ein vertraulicher OIDC-Client mit Authorization Code Flow und genau einem
konfigurierten Issuer. Nach dem Callback authentifiziert eine serverseitige
HTTP-Session die API-Aufrufe. Die Anwendung bietet weder einen eigenen
Passwortlogin noch einen Resource Server für Bearer-Tokens an.

**Begründung und Folgen:** Passwortspeicherung und Kontoverwaltung bleiben außerhalb
der Fachanwendung. Im Tracker sind keine Benutzerstammdaten erforderlich. Die Session
ermöglicht Browsernutzung ohne eine zusätzliche Tokenverwaltung im Frontend;
Cookie-Authentifizierung macht zugleich CSRF-Schutz für Schreibzugriffe notwendig.
SameSite=Lax und HttpOnly ergänzen diesen Schutz, ersetzen ihn aber nicht.

Browsernavigation kann zur Anmeldung umgeleitet werden, API-Aufrufe benötigen
stattdessen maschinenlesbare Fehler. Deshalb behandelt die Security-Filterkette
Authentifizierungs-, Zugriffs- und Callback-Fehler selbst als Problem Details.
`ApiExceptionHandler` ist für die nachgelagerte MVC-Verarbeitung zuständig.

Logout hat zwei Schritte: lokale Session-Invalidierung und browserseitiger
OIDC-Logout bei Keycloak. Nur das lokale Cookie zu entfernen würde die dortige
SSO-Session erhalten und einen erneuten Login ohne Passwort ermöglichen.

**Grenzen:** Sessions liegen im Anwendungsspeicher und gehen beim Tracker-Neustart
verloren. Eine weiterhin aktive Keycloak-Session kann einen neuen Login vereinfachen.
Kontosperrung oder Kontolöschung in Keycloak widerruft bestehende Tracker-Sessions
nicht unmittelbar; Back-Channel-Logout und zentrale Session-Verwaltung sind nicht
implementiert. Der lokale Inaktivitäts-Timeout beträgt 30 Minuten. Alle Nutzer des
konfigurierten Realms erhalten App-Zugang; es gibt keine zusätzliche Rollenprüfung.

**Nachweis:** [SecurityConfiguration](../src/main/java/de/example/expensetracker/security/SecurityConfiguration.java),
[AccountController](../src/main/java/de/example/expensetracker/security/AccountController.java),
[Session- und OIDC-Konfiguration](../src/main/resources/application.properties).

## 3. Autorisierung anhand des Subjects in jeder fachlichen Datenbankabfrage

**Kontext:** Erfolgreicher Login allein verhindert nicht den Zugriff auf Ausgaben
anderer Nutzer. Zusätzlich existieren Daten aus der Zeit ohne Benutzerzuordnung.

**Entscheidung:** `expenses.owner_subject` speichert das OIDC-Subject `sub`. Jede
fachliche Datenbankabfrage enthält den Besitzerfilter, auch Datumsfilter und
pessimistisch gesperrte Schreibzugriffe. Die Datenbank erzwingt diese Trennung nicht
über Row-Level Security; die Autorisierungsgrenze liegt in den Anwendungsabfragen.
Es gibt keinen Fremdschlüssel in die separate Keycloak-Datenbank.

**Begründung und Folgen:** Benutzername und E-Mail sind veränderlich und deshalb
ungeeignet als Besitzerschlüssel. Unter der Annahme genau eines Issuers identifiziert
`sub` den Nutzer ausreichend. Fremde und unbekannte IDs haben dasselbe 404-Verhalten,
damit Einzelzugriffe keinen Existenznachweis fremder Ausgaben liefern. Neue Abfragen
müssen diese Grenze ausdrücklich fortführen; eine Anmeldung am Controller ersetzt
den Besitzerfilter nicht.

Besitzerlose Altdaten bleiben unverändert gespeichert und unsichtbar. Die nullable
Spalte dient diesem Übergang; neue Ausgaben erhalten durch den Adapter zwingend
einen Besitzer. Die Migration weist vorhandene Daten keinem beliebigen ersten
Benutzer zu. Der Index auf Besitzer und ID unterstützt die gefilterten, nach ID
sortierten Zugriffe.

**Grenzen:** Die Identität ist über Issuer und Subject definiert, gespeichert wird
derzeit nur das Subject. Ein Realm-Wechsel erfordert daher eine bewusste Migration;
mehrere Issuer dürfen nicht einfach ergänzt werden. Nach Löschen und Neuanlegen
eines gleichnamigen Keycloak-Kontos bekommt dessen neue ID keinen Zugriff auf die
alten Ausgaben. Kontolöschung löst auch keine automatische Ausgabenlöschung aus.

**Nachweis:** [CurrentUser](../src/main/java/de/example/expensetracker/security/CurrentUser.java),
[Besitzerabfragen](../src/main/java/de/example/expensetracker/expense/ExpenseJpaRepository.java),
[Besitzermigration](../src/main/resources/db/changelog/002-expense-owner.sql).

## 4. Versioniertes Schema und getrennte Lebenszyklen der Daten

**Kontext:** Ausgaben und Identitäten müssen Neustarts überstehen, haben aber
unterschiedliche Verantwortlichkeiten. Lokale Einrichtung soll reproduzierbar sein.

**Entscheidung:** Liquibase verwaltet ausschließlich das Tracker-Schema; Hibernate
validiert es. Keycloak verwaltet sein eigenes Schema in einer separaten
PostgreSQL-Datenbank mit eigenem Volume. Der Realm-Import initialisiert eine neue
Installation und überschreibt einen bestehenden Realm nicht.

**Begründung und Folgen:** Die Anwendung hängt nicht vom internen Keycloak-Schema ab.
Schema-Upgrades des Trackers sind nachvollziehbar und unabhängig von dessen
Identitätsverwaltung. Benutzer- und Ausgabendaten bleiben beim normalen Stoppen
der Container erhalten. Die Importdatei ersetzt jedoch keine Migration bereits
vorhandener Realm- oder Client-Konfigurationen.

Pessimistische Schreibsperren im Tracker koordinieren konkurrierende PUT-/DELETE-
Zugriffe innerhalb ihrer Transaktionen. Sie schützen den vorhandenen Umgang mit
konkurrierenden Löschungen; eine optimistische Versionierung zur Erkennung veralteter
Clientdaten ist damit nicht verbunden.

**Grenzen:** Compose startet Keycloak im Entwicklungsmodus; die Anwendung läuft auf
dem Host. Daraus folgen unterschiedliche Wege für die Konfiguration: Compose liest
`.env`, die Java-Anwendung benötigt exportierte Variablen. Die OIDC-Discovery beim
App-Start setzt erreichbares Keycloak und ein übereinstimmendes Client-Secret beim
Token-Austausch voraus. HTTPS-Betrieb und verteilte Sessions sind kein Bestandteil
dieses lokalen Setups.

**Nachweis:** [Compose](../compose.yaml), [Realm-Import](../keycloak/expense-tracker-realm.json),
[Liquibase-Master](../src/main/resources/db/changelog/db.changelog-master.yaml).

## 5. Echte Datenbanktests und getrennte OIDC-Abnahme

**Kontext:** Besitzerfilter, PostgreSQL-Sperren und Schema-Upgrades sollen realistisch
geprüft werden, ohne jeden API-Test von einer laufenden Identitätsverwaltung abhängig
zu machen.

**Entscheidung:** Spring-Boot-/MockMvc-Tests verwenden PostgreSQL über Testcontainers
und führen Liquibase aus. Die gemeinsame Testkonfiguration ersetzt die OIDC-
Clientregistrierung; Tests setzen OIDC-Principals und CSRF-Token gezielt ein.
Die Security-Filter bleiben aktiv. Ein Migrationstest prüft zusätzlich den Übergang
vom ursprünglichen Schema mit vorhandenen Ausgaben.

**Begründung und Folgen:** PostgreSQL-spezifische Präzision und Sperrverhalten bleiben
prüfbar. Identitäten A und B lassen sich deterministisch für Autorisierungstests
verwenden. Die gemeinsame Datenbank und deren Bereinigung setzen aktuell
sequenzielle Testausführung voraus.

**Grenzen:** Diese Tests belegen keinen echten Authorization-Code-Austausch, keine
Passwortänderung bei Keycloak und keine dort abgeschlossene Abmeldung. Dafür dient
die reale Abnahme aus der README. Es gibt keinen eingecheckten automatisierten
End-to-End-Test mit echtem Keycloak; lokale Hilfsskripte und Berichte in `build/`
sind kein Ersatz. Aktuelle Testergebnisse gehören in den jeweiligen Arbeitsbericht,
nicht als dauerhaft gültige Erfolgsaussage in diese Architekturaufzeichnung.

**Nachweis:** [PostgresTestConfiguration](../src/test/java/de/example/expensetracker/PostgresTestConfiguration.java),
[SecurityApiTest](../src/test/java/de/example/expensetracker/SecurityApiTest.java),
[ExpenseMigrationTest](../src/test/java/de/example/expensetracker/ExpenseMigrationTest.java).


## 6. React als statischer Client unter derselben Origin

**Entscheidung:** React und TypeScript liegen unter `frontend/`; Vite erstellt die
statischen Ressourcen, die Gradle in das Spring-Boot-JAR übernimmt. Node und pnpm
sind ausschließlich Build-/Entwicklungswerkzeuge, kein zusätzlicher Laufzeitserver.
Ihre Versionen stehen in `frontend/.node-version` und `frontend/package.json`;
Abhängigkeiten sind exakt und über das pnpm-Lockfile festgehalten.

**Begründung und Folgen:** API und Browsernavigation verwenden dieselbe Origin.
Spring bleibt der einzige OIDC-Client, Tokenverwaltung im Browser entfällt.
`/api/me`, `/api/csrf` und das POST-Logout-Formular bilden die Sessionintegration.
Bei `401` wird der Query-Cache geleert; fehlgeschlagene Mutationen werden nicht
automatisch wiederholt. Nach erfolgreichem Login führt Spring auf `/`.
Browser-Callback-Fehler liefern eine HTML-Fehlerseite; API-orientierte Aufrufer
bekommen weiterhin Problem Details. Nur explizite UI-Routen liefern das SPA-HTML;
API-, Asset- und unbekannte Routen erhalten keinen pauschalen SPA-Fallback.

Im lokalen Entwicklungsprofil lädt Spring das HTML und die Module vom Vite-Server;
der Hot-Reload-WebSocket verwendet dessen Port direkt. Die Origin der Fach- und
Authentifizierungsaufrufe bleibt unverändert. Das Profil ist keine Betriebsoption.
Startanleitungen stehen ausschließlich in der README.

**Grenzen:** Die Anwendung bleibt auf lokalen HTTP-Betrieb ausgelegt. Smartphones
werden durch responsive Darstellung und Browser-Tests berücksichtigt; echter
Zugriff aus dem Heimnetz und öffentlicher Betrieb sind nicht eingerichtet.

## 7. Gemeinsame Buchungen bei kompatibler Ausgaben-API

**Entscheidung:** Die bestehende Tabelle erhält mit einem neuen Changeset eine
verpflichtende Buchungsart. Bestehende Zeilen sind `EXPENSE`. Ein gemeinsamer
Repository-Adapter verwendet die bisherige Entity für beide APIs. Die neue
`/api/bookings`-API verwaltet `INCOME` und `EXPENSE`; die bisherige API filtert bereits
in ihren Datenbankabfragen zusätzlich nach `EXPENSE`, auch bei Schreibsperren.
Besitzerfilter und unsichtbare besitzerlose Altdaten gelten für beide APIs.

**Begründung und Folgen:** Eine zweite Einnahmentabelle würde CRUD, Besitzertrennung
und Auswertungen verdoppeln. Positive Beträge plus explizite Art erhalten die
bisherige Betragsvalidierung. Die alte API bleibt einschließlich ihrer numerischen
JSON-Beträge kompatibel. Die neue API verwendet Dezimalstrings, damit JavaScript
keine Präzision beim JSON-Parsen verliert. Dezimalarithmetik im Client ermittelt
Summen ohne binäre Fließkommarechnung; Anzeige und Speicherung runden nicht auf
zwei Stellen. EUR ist zunächst eine gemeinsame Darstellungsannahme.

**Grenzen:** Die Übersicht lädt den vollständigen Zeitraum und summiert im Client.
Pagination und serverseitige Aggregationen fehlen. Schreibsperren verhindern keine
Überschreibung veralteter Formularstände. Ein Artenwechsel über die neue API kann
eine Buchung für die alte Ausgaben-API sichtbar oder unsichtbar machen.
