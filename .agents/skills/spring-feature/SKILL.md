---
name: spring-feature
description: Implementiere neue Spring-Boot-Funktionen im Expense-Tracker dieses Repositorys mit passenden Tests und Prüfung der bestehenden API-Verträge.
---

# Spring-Feature implementieren

1. Lies die `AGENTS.md` im Repository und gegebenenfalls weitere für die betroffenen
   Dateien geltende Anweisungen. Sie sind die Quelle für Projektkonventionen und
   Prüfpflichten. Prüfe `git status --short` und erhalte vorhandene Änderungen.
2. Untersuche den betroffenen Datenfluss unter
   `src/main/java/de/example/expensetracker`, die README und vorhandene Tests.
   Prüfe je nach Funktion Request-Validierung, Antwortformate und HTTP-Statuscodes
   sowie Transaktionen, Sperren und das Zusammenspiel von Entity und Liquibase.
   Leite daraus ab, welche bestehenden Verträge erhalten bleiben müssen.
3. Formuliere vor der Umsetzung einen kurzen Plan: gewünschtes Verhalten,
   betroffene Komponenten und aussagekräftige Tests. Kläre nur Anforderungen,
   deren Unklarheit die Implementierung wesentlich beeinflusst.
4. Ergänze nach Möglichkeit zuerst passende Tests im vorhandenen Testaufbau.
   Führe sie gezielt mit `./gradlew test --tests <Testklasse oder Testmethode>` aus
   und prüfe, dass ein erwartetes Scheitern auf fehlendes Verhalten zurückgeht.
   Decke relevante Erfolgs- und Fehlerfälle ab; koordiniere Nebenläufigkeitstests
   deterministisch mit begrenzten Wartezeiten.
5. Implementiere die kleinste zusammenhängende Änderung innerhalb der vorhandenen
   Architektur. Vermeide aufgabenfremde Refactorings. Aktualisiere bei Bedarf
   Migrationen und Dokumentation nach den Regeln aus `AGENTS.md`.
6. Führe nach der Änderung zuerst die relevanten Tests und danach `./gradlew test`
   im Repository-Verzeichnis aus. Erfülle zusätzlich die für die Änderung
   geltenden Prüfpflichten aus `AGENTS.md`. Diagnostiziere fehlgeschlagene Läufe;
   kennzeichne blockierte oder nicht ausgeführte Prüfungen ausdrücklich.
7. Prüfe `git diff`, gegebenenfalls `git diff --cached`, und `git diff --check`
   auf unbeabsichtigte Änderungen. Fasse Verhalten, betroffene Dateien,
   tatsächliche Testergebnisse und verbleibende Einschränkungen kurz zusammen.

Erstelle keinen Commit ohne ausdrückliche Aufforderung des Nutzers.
