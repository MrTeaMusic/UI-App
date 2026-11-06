package trainer.compiler;

import trainer.content.Content.SourceFile;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Kompiliert Quelltext mit dem echten Java-Compiler (javax.tools, wie javac)
 * und startet das Ergebnis in einer eigenen JVM. So kann System.exit() oder
 * EXIT_ON_CLOSE im Übungsprogramm den Trainer nicht mit beenden.
 */
public final class JavaRunner {

    /** Diese Imports werden auf Wunsch unsichtbar in Zeile 1 ergänzt (Zeilennummern bleiben gleich). */
    public static final String AUTO_IMPORTS = "import javax.swing.*; import javax.swing.table.*; import javax.swing.border.*; "
            + "import java.awt.*; import java.awt.event.*; import java.beans.*; import java.util.*; ";

    public record Problem(String file, long line, long column, boolean error, String message, String hint) {
        @Override
        public String toString() {
            return file + ":" + line + ": " + (error ? "Fehler" : "Warnung") + ": " + message;
        }
    }

    public record Result(boolean ok, List<Problem> problems, Path classDir) { }

    private JavaRunner() {
    }

    public static boolean compilerAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    /**
     * Kompiliert alle .java-Dateien und kopiert alle anderen Dateien (z. B. .properties)
     * in denselben Ausgabeordner.
     */
    public static Result compile(List<SourceFile> files, boolean autoImports) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Kein Java-Compiler gefunden. Starte den Trainer mit einem JDK, nicht nur mit einer JRE.");
        }
        Path out = Files.createTempDirectory("klausurtrainer");
        out.toFile().deleteOnExit();
        List<JavaFileObject> units = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        for (SourceFile f : files) {
            if (f.isJava()) {
                String prefix = autoImports ? importPrefix(f.text()) : "";
                prefixes.add(prefix);
                units.add(new Source(f.name(), prefix + f.text()));
            } else {
                Files.writeString(out.resolve(f.name()), f.text(), StandardCharsets.UTF_8);
            }
        }
        List<Problem> problems = new ArrayList<>();
        if (units.isEmpty()) {
            return new Result(true, problems, out);
        }
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        boolean ok;
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            List<String> options = List.of("-d", out.toString(), "-encoding", "UTF-8", "-Xlint:-options", "-proc:none");
            StringWriter sink = new StringWriter();
            ok = compiler.getTask(sink, fm, diags, options, null, units).call();
        }
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.NOTE) {
                continue;
            }
            String file = d.getSource() == null ? "?" : ((Source) d.getSource()).fileName;
            long line = d.getLineNumber();
            long col = d.getColumnNumber();
            int idx = d.getSource() == null ? -1 : units.indexOf(d.getSource());
            if (line == 1 && idx >= 0) {
                col = Math.max(1, col - prefixes.get(idx).length());
            }
            String msg = d.getMessage(Locale.ENGLISH);
            boolean error = d.getKind() == Diagnostic.Kind.ERROR;
            problems.add(new Problem(file, line, col, error, msg, ErrorHelp.explain(msg)));
        }
        return new Result(ok, problems, out);
    }

    private static String importPrefix(String text) {
        if (Pattern.compile("^\\s*package\\s", Pattern.MULTILINE).matcher(text).find()) {
            return "";
        }
        String prefix = AUTO_IMPORTS;
        // Mehrdeutige Namen eindeutig machen, außer der Nutzer importiert sie selbst
        if (!Pattern.compile("import\\s+[\\w.]+\\.List\\s*;").matcher(text).find()) {
            prefix += "import java.util.List; ";
        }
        if (!Pattern.compile("import\\s+[\\w.]+\\.Timer\\s*;").matcher(text).find()) {
            prefix += "import javax.swing.Timer; ";
        }
        return prefix;
    }

    /** Sucht die Klasse, die gestartet werden soll: bevorzugt eine mit main. */
    public static String findMainClass(List<SourceFile> files) {
        Pattern main = Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(");
        String found = null;
        for (SourceFile f : files) {
            if (f.isJava() && main.matcher(f.text()).find()) {
                String cls = className(f);
                if (found == null || cls.equals("Launcher")) {
                    found = cls;
                }
            }
        }
        return found;
    }

    /** Für Fenster oder Zeichenflächen ohne main: Klasse, die eine Vorschau bekommen kann. */
    public static String findPreviewClass(List<SourceFile> files) {
        Pattern p = Pattern.compile("class\\s+(\\w+)\\s+extends\\s+(JFrame|JPanel|JComponent)\\b");
        for (SourceFile f : files) {
            if (f.isJava()) {
                Matcher m = p.matcher(f.text());
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return null;
    }

    private static String className(SourceFile f) {
        return f.name().substring(0, f.name().length() - ".java".length());
    }

    /** Startet die Klasse in einer neuen JVM. Ausgaben gehen zeilenweise an out bzw. err. */
    public static Process run(Path classDir, String mainClass, String args,
                              Consumer<String> out, Consumer<String> err, Runnable onExit) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(classDir.toString());
        cmd.add(mainClass);
        if (args != null && !args.isBlank()) {
            cmd.addAll(List.of(args.trim().split("\\s+")));
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        Process p = pb.start();
        pump(p.getInputStream(), out);
        Thread t = pump(p.getErrorStream(), err);
        Thread waiter = new Thread(() -> {
            try {
                p.waitFor();
                t.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            onExit.run();
        }, "warte-auf-programm");
        waiter.setDaemon(true);
        waiter.start();
        return p;
    }

    /**
     * Erzeugt eine kleine Startklasse, die ein Fenster oder eine Zeichenfläche anzeigt.
     * Ein JFrame wird mit initialise()/initialize() gestartet, falls es die Methode ohne Parameter gibt.
     */
    public static SourceFile previewLauncher(String cls) {
        String code = """
                public class VorschauStarter {
                    public static void main(String[] args) throws Exception {
                        javax.swing.SwingUtilities.invokeAndWait(() -> {
                            try {
                                Object o = Class.forName("%s").getDeclaredConstructor().newInstance();
                                if (o instanceof javax.swing.JFrame f) {
                                    for (String name : new String[]{"initialise", "initialize"}) {
                                        try {
                                            java.lang.reflect.Method m = f.getClass().getDeclaredMethod(name);
                                            m.setAccessible(true);
                                            m.invoke(f);
                                            if (!f.isVisible()) { f.setVisible(true); }
                                            return;
                                        } catch (NoSuchMethodException ignored) { }
                                    }
                                    f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
                                    f.setSize(500, 400);
                                    f.setVisible(true);
                                } else {
                                    javax.swing.JFrame f = new javax.swing.JFrame("Vorschau: %s");
                                    f.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
                                    f.add((java.awt.Component) o);
                                    f.setSize(500, 400);
                                    f.setVisible(true);
                                }
                            } catch (NoSuchMethodException e) {
                                System.err.println("Vorschau geht nur mit einem Konstruktor ohne Parameter.");
                                System.exit(1);
                            } catch (ReflectiveOperationException e) {
                                Throwable c = e.getCause() != null ? e.getCause() : e;
                                c.printStackTrace();
                                System.exit(1);
                            }
                        });
                    }
                }
                """.formatted(cls, cls);
        return new SourceFile("VorschauStarter.java", code);
    }

    public static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException ignored) {
            // egal, ist nur ein temporärer Ordner
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Thread pump(InputStream in, Consumer<String> sink) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.startsWith("Picked up JAVA_TOOL_OPTIONS")) {
                        sink.accept(line);
                    }
                }
            } catch (IOException ignored) {
                // Prozess wurde beendet
            }
        }, "ausgabe");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static final class Source extends SimpleJavaFileObject {
        final String fileName;
        final String code;

        Source(String fileName, String code) {
            super(URI.create("string:///" + fileName), Kind.SOURCE);
            this.fileName = fileName;
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
