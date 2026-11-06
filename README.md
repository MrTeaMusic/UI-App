# Swing-Klausurtrainer

Eine Desktop-App zum Lernen für die Klausur **User Interaction** (Java Swing/AWT, 14.12.2026).
Du liest Erklärungen, schreibst echten Java-Code, und die App kompiliert ihn mit dem **echten Java-Compiler**,
startet dein Programm (Swing-Fenster öffnen sich wirklich) und prüft, ob alle geforderten Bausteine da sind.

## Starten in IntelliJ

1. **File → Open…** und diesen Ordner wählen. IntelliJ erkennt das Maven-Projekt (`pom.xml`) automatisch.
2. Unter **File → Project Structure → Project → SDK** ein **JDK 17 oder neuer** einstellen.
   Wichtig: ein JDK, keine reine JRE, denn der Trainer braucht den eingebauten Compiler.
3. Oben rechts die Run-Konfiguration **Klausurtrainer** wählen und auf den grünen Pfeil klicken.
   (Alternativ: `src/main/java/trainer/App.java` öffnen → Rechtsklick → *Run 'App.main()'*.)

Ohne IntelliJ: `mvn package` und dann `java -jar target/swing-klausurtrainer-1.0.jar`.

## Was drin ist

| Bereich | Inhalt |
|---|---|
| **Lernpfad** | 9 Kapitel von Java-Grundlagen bis Theorie, jeweils mit Erklärung und Beispielen |
| **Code-Aufgaben** | 38 Aufgaben, in denen du Code schreibst. Manche haben gegebene Klassen (z. B. das Model) als Tab dabei |
| **Altklausuren** | 2022 (Arbeitszeit), 2023 (Tortendiagramm), 2024 (Längen-Umrechner) mit allen Dateien, 100-Minuten-Uhr und Bewertung je Teilaufgabe |
| **Spielwiese** | Freies Üben mit beliebig vielen Dateien (View, Controller, Model, Launcher …) |

## So arbeitest du mit einer Aufgabe

| Knopf | Kürzel | Was passiert |
|---|---|---|
| **Kompilieren** | Strg+B | Der echte Compiler übersetzt deinen Code. Fehler werden rot markiert, mit Erklärung auf Deutsch. Klick auf einen Fehler springt hin. |
| **▶ Ausführen** | F5 | Kompiliert und startet dein Programm in einer eigenen JVM. Ausgaben stehen in der Konsole. Hat eine Klasse kein `main`, aber erweitert `JFrame` oder `JPanel`, zeigt der Trainer eine Vorschau. |
| **✓ Prüfen** | Strg+Enter | Kompiliert und hakt die Anforderungen links ab. Kompiliert alles und sind alle Bausteine da, ist die Aufgabe erledigt. |
| **Lösung** | | Zeigt die Musterlösung (erst selbst versuchen!). |
| **Zurücksetzen** | | Zurück zum Ausgangscode. |

Weitere Kürzel im Editor: Tab rückt ein, Shift+Tab rückt aus, Strg+/ kommentiert aus, Strg+Z macht rückgängig.

**Auto-Imports:** Standardmäßig ergänzt der Trainer unsichtbar `javax.swing.*`, `java.awt.*`, `java.awt.event.*`,
`java.beans.*` und `java.util.*` (in IntelliJ macht das sonst Alt+Enter). Schalte den Haken aus, wenn du Imports selbst üben willst.

**Speichern:** Dein Code und dein Fortschritt werden automatisch im Benutzerordner unter `.swing-klausurtrainer` gespeichert.

## Für Änderungen an den Inhalten

Alle Inhalte liegen als Textdateien in `src/main/resources/content/`:

- `modules/m0.txt` … `m8.txt`: Erklärung (`@@lesson`, HTML) und Aufgaben (`@@task`)
- `exams/k22.txt` … `k24.txt`: Altklausuren mit Dateien und Teilaufgaben (`@@step`)

Eine Aufgabe sieht so aus:

```
@@task m0-1
@@title Eine Klasse schreiben
@@text
Aufgabentext (HTML)
@@check private boolean on
private\s+boolean\s+on\s*;
@@starter Lamp.java
// Ausgangscode
@@given Hilfsklasse.java
// Gegebene Datei, nur lesbar
@@solution Lamp.java
// Musterlösung
```

Jedes `@@check` hat eine Beschriftung und darunter einen regulären Ausdruck, der im Code (ohne Kommentare) vorkommen muss.

Nach einer Änderung **Inhalte prüfen (SelfTest)** ausführen: Es kompiliert jede Musterlösung und prüft sie gegen ihre eigenen Checks.
Das läuft auch automatisch bei jedem Push (GitHub Actions).

## Aufbau des Codes

Die App ist selbst eine Swing-Anwendung, du kannst also auch ihren Code als Beispiel lesen.

| Datei | Aufgabe |
|---|---|
| `trainer/App.java` | Startpunkt (`main`) |
| `trainer/ui/MainWindow.java` | Hauptfenster mit Baum links (`JTree`, `JSplitPane`) |
| `trainer/ui/WorkspacePanel.java` | Arbeitsbereich: Aufgabe, Editor-Tabs, Probleme, Konsole |
| `trainer/ui/CodeEditor.java` | Editor mit Zeilennummern, Farben und Fehlermarkierung |
| `trainer/compiler/JavaRunner.java` | Kompilieren mit `javax.tools.JavaCompiler` und Starten in einer eigenen JVM |
| `trainer/compiler/ErrorHelp.java` | Deutsche Erklärungen für typische Compiler-Fehler |
| `trainer/check/Checker.java` | Prüft die geforderten Bausteine |
| `trainer/content/Content.java` | Liest die Inhalte |
| `trainer/Progress.java` | Speichert Fortschritt und Code |
