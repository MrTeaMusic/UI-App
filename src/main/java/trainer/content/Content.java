package trainer.content;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lädt alle Lerninhalte aus src/main/resources/content.
 *
 * Format: Zeilen, die mit "@@" beginnen, starten einen Abschnitt, z. B.
 * "@@task m0-1" oder "@@starter View.java". Alles bis zum nächsten "@@" gehört dazu.
 */
public final class Content {

    public record Check(String label, String regex) { }

    public record SourceFile(String name, String text) {
        public boolean isJava() {
            return name.endsWith(".java");
        }
    }

    /** Eine Aufgabe im Editor: Dateien zum Bearbeiten, gegebene Dateien, Prüfregeln, Lösung. */
    public record Task(String id, String title, String textHtml, String args,
                       List<Check> checks, List<SourceFile> starter,
                       List<SourceFile> given, List<SourceFile> solution) { }

    public record Module(String id, String title, String lessonHtml, List<Task> tasks) { }

    public record Step(int points, String title, String textHtml, String scope,
                       List<Check> checks, String hint) { }

    public record Exam(String id, String title, String introHtml,
                       List<SourceFile> starter, List<SourceFile> solution, List<Step> steps) { }

    private final List<Module> modules = new ArrayList<>();
    private final List<Exam> exams = new ArrayList<>();

    public List<Module> modules() {
        return modules;
    }

    public List<Exam> exams() {
        return exams;
    }

    public static Content load() {
        Content c = new Content();
        for (String line : read("index.txt").split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String[]> sections = sections(read(line));
            if (line.startsWith("modules/")) {
                c.modules.add(parseModule(sections));
            } else {
                c.exams.add(parseExam(sections));
            }
        }
        return c;
    }

    private static String read(String name) {
        try (InputStream in = Content.class.getResourceAsStream("/content/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Inhalt fehlt: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Zerlegt eine Datei in Abschnitte: {schlüssel, wert, inhalt}. */
    private static List<String[]> sections(String text) {
        List<String[]> out = new ArrayList<>();
        String key = null;
        String value = null;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("@@")) {
                if (key != null) {
                    out.add(new String[]{key, value, body.toString()});
                }
                String head = line.substring(2);
                int sp = head.indexOf(' ');
                key = sp < 0 ? head : head.substring(0, sp);
                value = sp < 0 ? "" : head.substring(sp + 1);
                body.setLength(0);
            } else if (key != null) {
                body.append(line).append('\n');
            }
        }
        if (key != null) {
            out.add(new String[]{key, value, body.toString()});
        }
        // Abschließende Leerzeilen entfernen, aber genau einen Zeilenumbruch behalten
        for (String[] s : out) {
            s[2] = s[2].replaceAll("\\s+$", "");
            if (!s[2].isEmpty()) {
                s[2] += "\n";
            }
        }
        return out;
    }

    private static Module parseModule(List<String[]> sections) {
        String id = null;
        String title = null;
        String lesson = "";
        List<Task> tasks = new ArrayList<>();
        TaskBuilder tb = null;
        for (String[] s : sections) {
            String k = s[0];
            if (k.equals("task")) {
                if (tb != null) {
                    tasks.add(tb.build());
                }
                tb = new TaskBuilder(s[1]);
            } else if (tb == null) {
                switch (k) {
                    case "module" -> id = s[1];
                    case "title" -> title = s[1];
                    case "lesson" -> lesson = s[2];
                    default -> { }
                }
            } else {
                tb.add(k, s[1], s[2]);
            }
        }
        if (tb != null) {
            tasks.add(tb.build());
        }
        return new Module(id, title, lesson, tasks);
    }

    private static Exam parseExam(List<String[]> sections) {
        String id = null;
        String title = null;
        String intro = "";
        Map<String, String> starter = new LinkedHashMap<>();
        Map<String, String> solution = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();
        StepBuilder sb = null;
        for (String[] s : sections) {
            String k = s[0];
            if (k.equals("step")) {
                if (sb != null) {
                    steps.add(sb.build());
                }
                sb = new StepBuilder(Integer.parseInt(s[1].trim()));
            } else if (sb == null) {
                switch (k) {
                    case "exam" -> id = s[1];
                    case "title" -> title = s[1];
                    case "intro" -> intro = s[2];
                    case "starter" -> starter.put(s[1], s[2]);
                    case "solution" -> solution.put(s[1], s[2]);
                    default -> { }
                }
            } else {
                switch (k) {
                    case "title" -> sb.title = s[1];
                    case "text" -> sb.text = s[2];
                    case "scope" -> sb.scope = s[1];
                    case "check" -> sb.checks.add(new Check(s[1], s[2].trim()));
                    case "hint" -> sb.hint = s[2];
                    default -> { }
                }
            }
        }
        if (sb != null) {
            steps.add(sb.build());
        }
        return new Exam(id, title, intro, files(starter), files(solution), steps);
    }

    private static List<SourceFile> files(Map<String, String> m) {
        List<SourceFile> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(new SourceFile(k, v)));
        return out;
    }

    private static final class TaskBuilder {
        final String id;
        String title = "";
        String text = "";
        String args = "";
        final List<Check> checks = new ArrayList<>();
        final Map<String, String> starter = new LinkedHashMap<>();
        final Map<String, String> given = new LinkedHashMap<>();
        final Map<String, String> solution = new LinkedHashMap<>();

        TaskBuilder(String id) {
            this.id = id;
        }

        void add(String k, String v, String body) {
            switch (k) {
                case "title" -> title = v;
                case "text" -> text = body;
                case "args" -> args = v;
                case "check" -> checks.add(new Check(v, body.trim()));
                case "starter" -> starter.put(v, body);
                case "given" -> given.put(v, body);
                case "solution" -> solution.put(v, body);
                default -> { }
            }
        }

        Task build() {
            return new Task(id, title, text, args, checks, files(starter), files(given), files(solution));
        }
    }

    private static final class StepBuilder {
        final int points;
        String title = "";
        String text = "";
        String scope = null;
        String hint = "";
        final List<Check> checks = new ArrayList<>();

        StepBuilder(int points) {
            this.points = points;
        }

        Step build() {
            return new Step(points, title, text, scope, checks, hint);
        }
    }
}
